package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.structure.ColumnStructure;
import com.siimkinks.sqlitemagic.structure.TableStructure;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.JsonConfig;
import com.siimkinks.sqlitemagic.util.TopsortTables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic;

import rx.Subscription;

import static com.siimkinks.sqlitemagic.Const.CLASS_MODIFIERS;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.CLASS_NAME_GENERATED_CLASSES_MANAGER;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_COLUMN_FOR_VALUE;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_CONFIGURE_DATABASE;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_CREATE_TABLES;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_NAME;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_VERSION;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_NR_OF_TABLES;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.COMPILED_N_COLUMNS_SELECT;
import static com.siimkinks.sqlitemagic.WriterUtil.COMPILED_N_COLUMNS_SELECT_IMPL;
import static com.siimkinks.sqlitemagic.WriterUtil.FAST_CURSOR;
import static com.siimkinks.sqlitemagic.WriterUtil.FROM;
import static com.siimkinks.sqlitemagic.WriterUtil.MUTABLE_INT;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.NULLABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.SIMPLE_ARRAY_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING_ARRAY_SET;
import static com.siimkinks.sqlitemagic.WriterUtil.TABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.anyWildcardTypeName;
import static com.siimkinks.sqlitemagic.WriterUtil.createMagicInvokableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.notNullParameter;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_TABLE_SCHEMA;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_VIEW_QUERY;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CREATE_VIEW;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.writer.ColumnClassWriter.VAL_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedHandlerClassName;

/**
 * @author Siim Kinks
 */
@Singleton
public class GenClassesManagerWriter {

	@Inject
	public GenClassesManagerWriter() {
	}

	public void writeSource(Environment environment, GenClassesManagerStep managerStep) throws IOException {
		if (!environment.getAllTableElements().isEmpty()) {
			Filer filer = environment.getFiler();
			final MethodSpec executeViewCreate = executeViewCreate();
			TypeSpec.Builder classBuilder = TypeSpec.classBuilder(CLASS_NAME_GENERATED_CLASSES_MANAGER)
					.addModifiers(CLASS_MODIFIERS)
					.addMethod(databaseConfigurator(environment))
					.addMethod(databaseSchemaCreator(environment, managerStep, executeViewCreate))
					.addMethod(executeViewCreate)
					.addMethod(nrOfTables(environment))
					.addMethod(dbVersion(environment))
					.addMethod(dbName(environment))
					.addMethod(columnForValue(environment, managerStep));
			WriterUtil.writeSource(filer, classBuilder.build(), PACKAGE_ROOT);
			persistLatestStructure(environment);
		}
	}

	private void persistLatestStructure(Environment environment) {
		final List<TableElement> allTableElements = environment.getAllTableElements();
		final HashMap<String, TableStructure> structure = new HashMap<>(allTableElements.size());
		for (TableElement tableElement : allTableElements) {
			final List<ColumnElement> allColumns = tableElement.getAllColumns();
			final ArrayList<ColumnStructure> columns = new ArrayList<>(allColumns.size());
			for (ColumnElement columnElement : allColumns) {
				columns.add(ColumnStructure.create(columnElement));
			}
			structure.put(tableElement.getTableName(), TableStructure.create(tableElement, columns));
		}
		try {
			final File latestStructDir = new File(System.getProperty("PROJECT_DIR"), "db");
			if (!latestStructDir.exists()) {
				latestStructDir.mkdirs();
			}
			final File latestStructureFile = new File(latestStructDir, "latest.struct");
			JsonConfig.OBJECT_MAPPER.writeValue(latestStructureFile, structure);
		} catch (IOException e) {
			environment.getMessager().printMessage(Diagnostic.Kind.WARNING, "Error persisting latest schema graph");
		}
	}

	private MethodSpec databaseConfigurator(Environment environment) {
		MethodSpec.Builder method = createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_CONFIGURE_DATABASE)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(SQLITE_DATABASE, "db");
		if (hasAnyForeignKeys(environment.getAllTableElements())) {
			method.addStatement("db.setForeignKeyConstraintsEnabled(true)");
		}
		return method.build();
	}

	private boolean hasAnyForeignKeys(List<TableElement> allTableElements) {
		for (TableElement allTableElement : allTableElements) {
			for (ColumnElement columnElement : allTableElement.getColumnsExceptId()) {
				if (columnElement.isOnDeleteCascade()) {
					return true;
				}
			}
		}
		return false;
	}

	private MethodSpec databaseSchemaCreator(Environment environment, GenClassesManagerStep managerStep, MethodSpec executeViewCreate) {
		MethodSpec.Builder method = createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_CREATE_TABLES);
		final CodeBlock.Builder sqlTransactionBody = CodeBlock.builder();
		sqlTransactionBody.add(buildSchemaCreations(environment));
		sqlTransactionBody.add(buildViewSchemaCreations(managerStep, executeViewCreate));
		return WriterUtil.buildSqlTransactionMethod(method, sqlTransactionBody.build());
	}

	private CodeBlock buildViewSchemaCreations(GenClassesManagerStep managerStep, MethodSpec executeViewCreate) {
		final CodeBlock.Builder builder = CodeBlock.builder();
		WriterUtil.addDebugLogging(builder, "Creating views");
		final List<ViewElement> allViewElements = managerStep.getAllViewElements();
		for (ViewElement viewElement : allViewElements) {
			final ClassName viewDao = EntityEnvironment.getGeneratedDaoClassName(viewElement);
			builder.addStatement("$N(db, $T.$L, $S)",
					executeViewCreate,
					viewDao,
					FIELD_VIEW_QUERY,
					viewElement.getViewName());
		}
		return builder.build();
	}

	private MethodSpec executeViewCreate() {
		return MethodSpec.methodBuilder(METHOD_CREATE_VIEW)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(notNullParameter(SQLITE_DATABASE, "db"))
				.addParameter(notNullParameter(COMPILED_N_COLUMNS_SELECT, "query"))
				.addParameter(notNullParameter(STRING, "viewName"))
				.addStatement("final $1T queryImpl = ($1T) query",
						COMPILED_N_COLUMNS_SELECT_IMPL)
				.addStatement("final String[] args = queryImpl.args")
				.beginControlFlow("if (args != null)")
				.addStatement("db.execSQL(\"CREATE VIEW IF NOT EXISTS \" + viewName + \" AS \" + queryImpl.sql, args)")
				.nextControlFlow("else")
				.addStatement("db.execSQL(\"CREATE VIEW IF NOT EXISTS \" + viewName + \" AS \" + queryImpl.sql)")
				.endControlFlow()
				.build();
	}

	private CodeBlock buildSchemaCreations(Environment environment) {
		CodeBlock.Builder builder = CodeBlock.builder();
		WriterUtil.addDebugLogging(builder, "Creating tables");
		for (TableElement tableElement : TopsortTables.sort(environment)) {
			ClassName modelHandler = getGeneratedHandlerClassName(tableElement);
			builder.addStatement("db.execSQL($T.$L)", modelHandler, FIELD_TABLE_SCHEMA);
		}
		return builder.build();
	}

	private MethodSpec nrOfTables(Environment environment) {
		return createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_GET_NR_OF_TABLES)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.returns(TypeName.INT)
				.addStatement("return $L", environment.getAllTableElements().size())
				.build();
	}

	private MethodSpec dbVersion(Environment environment) {
		final Integer dbVersion = environment.getDbVersion();
		return createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_GET_DB_VERSION)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.returns(TypeName.INT)
				.addStatement("return $L", (dbVersion != null) ? dbVersion : 1)
				.build();
	}

	private MethodSpec dbName(Environment environment) {
		return createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_GET_DB_NAME)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.returns(String.class)
				.addStatement("return $L", environment.getDbName())
				.build();
	}

	private MethodSpec columnForValue(Environment environment, GenClassesManagerStep managerStep) {
		final TypeVariableName valType = TypeVariableName.get("V");
		final ParameterizedTypeName returnType = ParameterizedTypeName.get(COLUMN, valType, valType, valType, anyWildcardTypeName());
		final MethodSpec.Builder builder = createMagicInvokableMethod(CLASS_NAME_GENERATED_CLASSES_MANAGER, METHOD_COLUMN_FOR_VALUE)
				.addTypeVariable(valType)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(notNullParameter(valType, VAL_VARIABLE))
				.returns(returnType)
				.beginControlFlow("if ($L == null)", VAL_VARIABLE)
				.addStatement("throw new $T($S)", NullPointerException.class, "Value cannot be null")
				.endControlFlow()
				.addStatement("final $T className = val.getClass().getCanonicalName()", STRING)
				.addStatement("final $T strVal", STRING)
				.beginControlFlow("switch (className)");
		final CodeBlock.Builder switchBody = CodeBlock.builder();
		int i = 0;
		for (TransformerElement transformer : managerStep.getAllTransformerElements().values()) {
			final String objValName = "objVal" + i;
			switchBody.add("case $S:\n", transformer.getQualifiedDeserializedName())
					.indent()
					.addStatement("final $1T $2L = ($1T) $3L",
							transformer.getDeserializedType().asTypeName(),
							objValName,
							VAL_VARIABLE);
			final ExtendedTypeElement serializedType = transformer.getSerializedType();
			final FormatData valueGetter = transformer.serializedValueGetter(objValName);
			if (serializedType.isPrimitiveElement()) {
				final TypeName boxedType = TypeName.get(serializedType.getTypeMirror()).box();
				switchBody.addStatement(String.format("strVal = $T.toString(%s)", valueGetter.getFormat()),
						valueGetter.getWithOtherArgsBefore(boxedType));
			} else if (serializedType.isStringType(environment)) {
				switchBody.addStatement(valueGetter.formatInto("strVal = %s"), valueGetter.getArgs());
			} else {
				switchBody.addStatement(String.format("strVal = %s.toString()", valueGetter.getFormat()),
						valueGetter.getArgs());
			}
			switchBody.addStatement("return new $T($T.ANONYMOUS_TABLE, strVal, false, null)",
					ClassName.get(PACKAGE_ROOT, ColumnClassWriter.getClassName(transformer)),
					TABLE)
					.unindent();
			i++;
		}
		switchBody.add("default:\n")
				.indent()
				.addStatement("return new $T<>($T.ANONYMOUS_TABLE, \"'\" + val.toString() + \"'\", false, $T.STRING_PARSER, false, null)",
						COLUMN,
						TABLE,
						UTIL)
				.unindent();
		builder.addCode(switchBody.build())
				.endControlFlow();
		return builder.build();
	}

	static String loadFromCursorMethodParams() {
		return "cursor, columns, tableGraphNodeNames, queryDeep";
	}

	static void addLoadFromCursorMethodParams(MethodSpec.Builder builder) {
		builder.addParameter(notNullParameter(FAST_CURSOR, "cursor"))
				.addParameter(columnsParam())
				.addParameter(tableGraphNodeNamesParam())
				.addParameter(boolean.class, "queryDeep");
	}

	static ParameterSpec columnsParam() {
		return ParameterSpec.builder(ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, STRING, TypeName.INT.box()),
				"columns")
				.addAnnotation(NULLABLE)
				.build();
	}

	static ParameterSpec columnOffsetParam() {
		return ParameterSpec.builder(MUTABLE_INT, "columnOffset")
				.addAnnotation(NON_NULL)
				.build();
	}

	static ParameterSpec fromSelectClauseParam() {
		return ParameterSpec.builder(FROM,
				"from")
				.addAnnotation(NON_NULL)
				.build();
	}

	static ParameterSpec selectFromTablesParam() {
		return ParameterSpec.builder(STRING_ARRAY_SET,
				"selectFromTables")
				.addAnnotation(NULLABLE)
				.build();
	}

	static ParameterSpec tableGraphNodeNamesParam() {
		return ParameterSpec.builder(ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, STRING, STRING),
				"tableGraphNodeNames")
				.build();
	}

	static ParameterSpec select1Param() {
		return ParameterSpec.builder(TypeName.BOOLEAN,
				"select1")
				.build();
	}

	static ParameterSpec subscriptionParam() {
		return ParameterSpec.builder(Subscription.class, "subscription")
				.addAnnotation(NON_NULL)
				.build();
	}
}
