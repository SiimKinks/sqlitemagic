package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.ReturnCallback;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_UNSUBSCRIBED_UNEXPECTEDLY;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_INSERT_ERR_MSG;
import static com.siimkinks.sqlitemagic.SqliteMagicProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.WriterUtil.CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_INSERT_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_INSERT_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.addCallableToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addConflictAlgorithmToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromCallableParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleOnSubscribeToType;
import static com.siimkinks.sqlitemagic.WriterUtil.conflictAlgorithmParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbVariablesForOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.ifSubscriberUnsubscribed;
import static com.siimkinks.sqlitemagic.WriterUtil.insertStatementVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_CONTENT_VALUES_EXCEPT_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_INSERT_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_INSERT_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INSERT_INTERNAL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INSERT_WITH_CONFLICT_ALGORITHM_INTERNAL;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnFromProvidedIdsBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnToStatementBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCallToComplexColumnsOperationWithContentValuesIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addImmutableIdsParameterIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addInlineExecuteInsertWithCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addMethodInternalCallOnComplexColumnsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxSingleTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addSetIdStatementIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.contentValuesAndDbVariables;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.isIdSettingNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.statementWithImmutableIdsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.CONFLICT_ALGORITHM_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InsertWriter implements OperationWriter {

	private final EntityEnvironment entityEnvironment;
	private final TableElement tableElement;
	private final TypeName tableElementTypeName;
	private final Set<TableElement> allTableTriggers;

	private final TypeName iterable;
	private final ClassName daoClassName;

	private MethodSpec executeInsert;

	public static InsertWriter create(EntityEnvironment entityEnvironment, Set<TableElement> allTableTriggers) {
		final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
		return builder()
				.entityEnvironment(entityEnvironment)
				.tableElement(entityEnvironment.getTableElement())
				.tableElementTypeName(tableElementTypeName)
				.allTableTriggers(allTableTriggers)
				.iterable(typedIterable(tableElementTypeName))
				.daoClassName(entityEnvironment.getDaoClassName())
				.build();
	}

	@Override
	public void writeDao(TypeSpec.Builder classBuilder) {
		classBuilder.addMethod(bindToInsertStatement());
		addInsertMethodInternalCallOnComplexColumnsIfNeeded(classBuilder);
		addInsertWithConflictAlgorithmMethodInternalCallOnComplexColumnsIfNeeded(classBuilder);
	}

	public void writeHandler(TypeSpec.Builder classBuilder) {
		final MethodSpec executeInsert = getExecuteInsert();
		final MethodSpec internalInsert = addInternalInsert(classBuilder, executeInsert);
		final MethodSpec internalInsertWithConflictAlgorithm = insertWithConflictAlgorithmInternal();
		classBuilder.addMethod(executeInsert)
				.addMethod(internalInsertWithConflictAlgorithm)
				.addType(insert(internalInsert, internalInsertWithConflictAlgorithm))
				.addType(bulkInsert());
	}

	// -------------------------------------------
	//                  DAO methods
	// -------------------------------------------

	private MethodSpec bindToInsertStatement() {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_INSERT_STATEMENT)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(SQLITE_STATEMENT, "statement")
				.addParameter(entityParameter(tableElementTypeName))
				.addStatement("statement.clearBindings()");
		addImmutableIdsParameterIfNeeded(builder, tableElement);
		int colPos = 1;
		int immutableIdColPos = 0;
		for (ColumnElement columnElement : tableElement.getAllColumns()) {
			if (columnElement.isId() && columnElement.isAutoincrementId()) {
				continue;
			}
			if (columnElement.isHandledRecursively() && columnElement.isReferencedTableImmutable()) {
				addBindColumnFromProvidedIdsBlock(builder, columnElement, colPos, immutableIdColPos);
				immutableIdColPos++;
			} else {
				addBindColumnToStatementBlock(builder, colPos, columnElement);
			}
			colPos++;
		}
		return builder.build();
	}

	private void addInsertMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder) {
		addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS,
				COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
				new ReturnCallback<String, ColumnElement>() {
					@Override
					public String call(ColumnElement columnElement) {
						return columnElement.getReferencedTable().hasAnyPersistedComplexColumns() ? METHOD_INSERT_INTERNAL : METHOD_EXECUTE_INSERT;
					}
				},
				connectionImplParameter());
	}

	private void addInsertWithConflictAlgorithmMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder) {
		addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_INSERT_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS, new ReturnCallback<String, ColumnElement>() {
					@Override
					public String call(ColumnElement columnElement) {
						return METHOD_INSERT_WITH_CONFLICT_ALGORITHM_INTERNAL;
					}
				},
				ParameterSpec.builder(CONTENT_VALUES, "values").build(),
				ParameterSpec.builder(SQLITE_DATABASE, "db").build(),
				conflictAlgorithmParameter());
	}

	// -------------------------------------------
	//                  Handler methods
	// -------------------------------------------

	private MethodSpec addInternalInsert(TypeSpec.Builder handlerClassBuilder, MethodSpec executeInsert) {
		if (tableElement.hasAnyPersistedComplexColumns()) {
			final MethodSpec method = insertInternal(executeInsert);
			handlerClassBuilder.addMethod(method);
			return method;
		}
		return executeInsert;
	}

	private MethodSpec executeInsert() {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE_INSERT)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(entityParameter(tableElementTypeName))
				.addParameter(entityDbManagerParameter())
				.returns(TypeName.LONG);
		addImmutableIdsParameterIfNeeded(builder, tableElement);
		addInsertLoggingStatement(builder, tableElement);
		builder.addStatement("final long id")
				.addCode(insertStatementVariable())
				.beginControlFlow("synchronized (stm)");
		addBindToInsertStatement(builder, tableElement, daoClassName, "stm");
		builder.addStatement("id = stm.executeInsert()")
				.endControlFlow();
		addAfterInsertLoggingStatement(builder);
		addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
		addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
		return builder.addStatement("return id")
				.build();
	}

	static void addBindToInsertStatement(MethodSpec.Builder builder, TableElement tableElement, ClassName daoClassName, String insertStmVariableName) {
		builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "$T.$L($L, $L",
				daoClassName,
				METHOD_BIND_TO_INSERT_STATEMENT,
				insertStmVariableName,
				ENTITY_VARIABLE));
	}

	private MethodSpec insertInternal(MethodSpec executeInsert) {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INSERT_INTERNAL)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(entityParameter(tableElementTypeName))
				.addParameter(entityDbManagerParameter())
				.returns(TypeName.LONG);
		addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
		return builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "return $N($L, $L", executeInsert, ENTITY_VARIABLE, MANAGER_VARIABLE))
				.build();
	}

	private MethodSpec insertWithConflictAlgorithmInternal() {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INSERT_WITH_CONFLICT_ALGORITHM_INTERNAL)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(entityParameter(tableElementTypeName))
				.addParameter(CONTENT_VALUES, "values")
				.addParameter(SQLITE_DATABASE, "db")
				.addParameter(conflictAlgorithmParameter())
				.returns(TypeName.LONG);
		addCallToComplexColumnsOperationWithContentValuesIfNeeded(builder, entityEnvironment,
				METHOD_CALL_INTERNAL_INSERT_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS,
				"values", "db", "conflictAlgorithm");
		addInsertLoggingStatement(builder, tableElement);
		final String bindMethodName = tableElement.getIdColumn().isAutoincrementId() ? METHOD_BIND_TO_CONTENT_VALUES_EXCEPT_ID : METHOD_BIND_TO_CONTENT_VALUES;
		builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "$T.$L($L, values", daoClassName, bindMethodName, ENTITY_VARIABLE))
				.addStatement("final long id = db.insertWithOnConflict($S, null, values, conflictAlgorithm)", tableElement.getTableName());
		addAfterInsertLoggingStatement(builder);
		addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
		addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
		builder.addStatement("return id");
		return builder.build();
	}

	private TypeSpec insert(MethodSpec insert, MethodSpec insertWithConflictAlgorithm) {
		final MethodSpec insertExecute = insertExecute(insert, insertWithConflictAlgorithm);
		final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_INSERT, ENTITY_INSERT_BUILDER, tableElementTypeName, ENTITY_VARIABLE);
		addConflictAlgorithmToOperationBuilder(builder, ENTITY_INSERT_BUILDER);
		return builder.addSuperinterface(ENTITY_INSERT_BUILDER)
				.addMethod(insertExecute)
				.addMethod(insertObserve(builder, insertExecute))
				.build();
	}

	private MethodSpec insertExecute(MethodSpec insert, MethodSpec insertWithConflictAlgorithm) {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(TypeName.LONG)
				.addCode(entityDbVariablesForOperationBuilder(tableElement));

		final boolean hasAnyPersistedComplexColumns = tableElement.hasAnyPersistedComplexColumns();
		addTopMethodStartBlock(builder, hasAnyPersistedComplexColumns);

		builder.addStatement("final $T id", TypeName.LONG)
				.beginControlFlow("if ($N == SQLiteDatabase.CONFLICT_NONE || $N == SQLiteDatabase.CONFLICT_ABORT)",
						CONFLICT_ALGORITHM_VARIABLE, CONFLICT_ALGORITHM_VARIABLE)
				.addStatement("id = $N($L, $L)", insert, ENTITY_VARIABLE, MANAGER_VARIABLE)
				.nextControlFlow("else")
				.addCode(contentValuesAndDbVariables())
				.addStatement("id = $N($L, values, db, conflictAlgorithm)", insertWithConflictAlgorithm, ENTITY_VARIABLE)
				.endControlFlow();

		final String returnStatement = "return id";
		final String failReturnStatement = "return -1";
		addTopMethodEndBlock(builder, allTableTriggers, hasAnyPersistedComplexColumns, returnStatement, failReturnStatement);

		return builder.build();
	}

	private MethodSpec insertObserve(TypeSpec.Builder typeBuilder, final MethodSpec insertExecute) {
		final TypeName entityTypeName = TypeName.LONG.box();
		final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
				.addAnnotation(Override.class);
		addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
			@Override
			public void call(MethodSpec.Builder builder) {
				builder.addStatement("final $T id = $N()", TypeName.LONG, insertExecute)
						.beginControlFlow("if (id == -1)")
						.addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, FAILED_TO_INSERT_ERR_MSG)
						.endControlFlow()
						.addStatement("return id");
			}
		});
		addRxSingleCreateFromCallableParentClass(builder);
		return builder.build();
	}

	private TypeSpec bulkInsert() {
		final ParameterizedTypeName interfaceType = ParameterizedTypeName.get(ENTITY_BULK_INSERT_BUILDER, tableElementTypeName);
		final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_INSERT, interfaceType, iterable, OBJECTS_VARIABLE);
		return builder
				.addSuperinterface(interfaceType)
				.addMethod(bulkInsertExecute())
				.addMethod(bulkInsertObserve(builder))
				.build();
	}

	private MethodSpec bulkInsertObserve(TypeSpec.Builder typeBuilder) {
		final TypeName entityTypeName = TypeName.BOOLEAN.box();
		final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
				.addAnnotation(Override.class);
		addRxSingleCreateFromParentClass(builder);
		addRxSingleOnSubscribeToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
			@Override
			public void call(MethodSpec.Builder builder) {
				builder.addCode(entityDbVariablesForOperationBuilder(tableElement));
				addTransactionStartBlock(builder);
				builder.addCode(insertStatementVariable())
						.beginControlFlow("synchronized (stm)")
						.beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
				addInsertLoggingStatement(builder, tableElement);
				addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
				addBindToInsertStatement(builder, tableElement, daoClassName, "stm");
				builder.addStatement("final long id = stm.executeInsert()");
				addAfterInsertLoggingStatement(builder);
				addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
				addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
				builder.beginControlFlow(ifSubscriberUnsubscribed())
						.addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, ERROR_UNSUBSCRIBED_UNEXPECTEDLY)
						.endControlFlow()
						.endControlFlow()
						.endControlFlow();
				addRxSingleTransactionEndBlock(builder, allTableTriggers, "Boolean.TRUE");
			}
		});
		return builder.build();
	}

	private MethodSpec bulkInsertExecute() {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(TypeName.BOOLEAN)
				.addCode(entityDbVariablesForOperationBuilder(tableElement));
		addTransactionStartBlock(builder);
		builder.addCode(insertStatementVariable())
				.beginControlFlow("synchronized (stm)")
				.beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
		addInsertLoggingStatement(builder, tableElement);
		addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
		builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "$T.$L(stm, entity", daoClassName, METHOD_BIND_TO_INSERT_STATEMENT));
		if (!GENERATE_LOGGING && !isIdSettingNeeded(tableElement)) {
			addInlineExecuteInsertWithCheckIdValidity(builder, "stm", FAILED_TO_INSERT_ERR_MSG);
		} else {
			builder.addStatement("final long id = stm.executeInsert()");
			addAfterInsertLoggingStatement(builder);
			addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
			addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
		}
		builder.endControlFlow()
				.endControlFlow();
		addTransactionEndBlock(builder, allTableTriggers, "return true", "return false");
		return builder.build();
	}

	public MethodSpec getExecuteInsert() {
		if (executeInsert == null) {
			executeInsert = executeInsert();
		}
		return executeInsert;
	}

	private void addCallToInternalInsertOnComplexColumnsIfNeeded(EntityEnvironment entityEnvironment, MethodSpec.Builder builder) {
		if (tableElement.hasAnyPersistedComplexColumns()) {
			CodeBlock.Builder codeBuilder = CodeBlock.builder();
			if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
				codeBuilder.add("long[] ids = ");
			}
			codeBuilder.add("$T.$L($L, $L.getDbConnection())", entityEnvironment.getDaoClassName(), METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS, ENTITY_VARIABLE, MANAGER_VARIABLE)
					.add(WriterUtil.codeBlockEnd());
			builder.addCode(codeBuilder.build());
		}
	}

	private void addInsertLoggingStatement(MethodSpec.Builder builder, TableElement tableElement) {
		if (GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"INSERT\\n  table: $L\\n  object: %s\", $L.toString())",
					SQLITE_MAGIC, LOG_UTIL, tableElement.getTableName(), ENTITY_VARIABLE);
		}
	}

	private void addAfterInsertLoggingStatement(MethodSpec.Builder builder) {
		if (GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"INSERT id: %s\", id)", SQLITE_MAGIC, LOG_UTIL);
		}
	}
}
