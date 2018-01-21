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
import com.siimkinks.sqlitemagic.util.Callback2;
import com.siimkinks.sqlitemagic.util.Dual;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import static com.siimkinks.sqlitemagic.Const.CLASS_MODIFIERS;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_CLEAR_DATA;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_COLUMN_FOR_VALUE;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_CONFIGURE_DATABASE;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_CREATE_TABLES;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_NAME;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_VERSION;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_NR_OF_TABLES;
import static com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_SUBMODULE_NAMES;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.FROM;
import static com.siimkinks.sqlitemagic.WriterUtil.MUTABLE_INT;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.NOT_NULLABLE_COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.NULLABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.SIMPLE_ARRAY_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.SQL_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING_ARRAY_SET;
import static com.siimkinks.sqlitemagic.WriterUtil.SUPPORT_SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.TABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.addDebugLogging;
import static com.siimkinks.sqlitemagic.WriterUtil.anyWildcardTypeName;
import static com.siimkinks.sqlitemagic.WriterUtil.createMagicInvokableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.notNullParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.nullableParameter;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_TABLE_SCHEMA;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_VIEW_QUERY;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_COLUMN_FOR_VALUE_OR_NULL;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.writer.ColumnClassWriter.VAL_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedHandlerClassName;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MODULE_NAME_VARIABLE;

@Singleton
public class GenClassesManagerWriter {

  @Inject
  public GenClassesManagerWriter() {
  }

  public void writeSource(Environment environment, GenClassesManagerStep managerStep) throws IOException {
    if (!environment.getAllTableElements().isEmpty()) {
      final Filer filer = environment.getFiler();
      final String className = environment.getGenClassesManagerClassName();
      TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
          .addModifiers(CLASS_MODIFIERS)
          .addMethod(databaseConfigurator(environment, className))
          .addMethod(databaseSchemaCreator(environment, managerStep, className))
          .addMethod(clearData(environment, className))
          .addMethod(nrOfTables(environment, className));

      if (!environment.isSubmodule()) {
        classBuilder
            .addMethod(columnForValue(environment, managerStep, className))
            .addMethod(dbVersion(environment, className))
            .addMethod(dbName(environment, className))
            .addMethod(submoduleNames(environment, className));
      } else {
        classBuilder.addMethod(columnForValueOrNull(environment, managerStep, className));
      }
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
      final File latestStructDir = new File(environment.getProjectDir(), "db");
      if (!latestStructDir.exists()) {
        latestStructDir.mkdirs();
      }
      final File latestStructureFile = new File(latestStructDir, "latest.struct");
      JsonConfig.OBJECT_MAPPER.writeValue(latestStructureFile, structure);
    } catch (IOException e) {
      environment.getMessager().printMessage(Diagnostic.Kind.WARNING, "Error persisting latest schema graph");
    }
  }

  private MethodSpec databaseConfigurator(Environment environment, String className) {
    final MethodSpec.Builder method = createMagicInvokableMethod(className, METHOD_CONFIGURE_DATABASE)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(SUPPORT_SQLITE_DATABASE, "db");
    if (hasAnyForeignKeys(environment.getAllTableElements())) {
      method.addStatement("db.setForeignKeyConstraintsEnabled(true)");
    }
    forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
      @Override
      public void call(TypeMirror type, String moduleName) {
        method.addStatement("$T.$L(db)",
            type,
            METHOD_CONFIGURE_DATABASE);
      }
    });
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

  private MethodSpec databaseSchemaCreator(Environment environment, GenClassesManagerStep managerStep, String className) {
    final MethodSpec.Builder method = createMagicInvokableMethod(className, METHOD_CREATE_TABLES);
    final CodeBlock.Builder sqlTransactionBody = CodeBlock.builder();
    addSubmoduleSchemasIfNeeded(environment, sqlTransactionBody);
    sqlTransactionBody.add(buildSchemaCreations(environment));
    sqlTransactionBody.add(buildViewSchemaCreations(managerStep));
    return WriterUtil.buildSqlTransactionMethod(method, sqlTransactionBody.build());
  }

  private void addSubmoduleSchemasIfNeeded(Environment environment, final CodeBlock.Builder sqlTransactionBody) {
    forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
      @Override
      public void call(TypeMirror type, String moduleName) {
        sqlTransactionBody.addStatement("$T.$L(db)",
            type,
            METHOD_CREATE_TABLES);
      }
    });
  }

  private CodeBlock buildSchemaCreations(Environment environment) {
    CodeBlock.Builder builder = CodeBlock.builder();
    addDebugLogging(builder, "Creating tables");
    for (TableElement tableElement : TopsortTables.sort(environment)) {
      ClassName modelHandler = getGeneratedHandlerClassName(tableElement);
      builder.addStatement("db.execSQL($T.$L)", modelHandler, FIELD_TABLE_SCHEMA);
    }
    return builder.build();
  }

  private CodeBlock buildViewSchemaCreations(GenClassesManagerStep managerStep) {
    final CodeBlock.Builder builder = CodeBlock.builder();
    addDebugLogging(builder, "Creating views");
    final List<ViewElement> allViewElements = managerStep.getAllViewElements();
    for (ViewElement viewElement : allViewElements) {
      final ClassName viewDao = EntityEnvironment.getGeneratedDaoClassName(viewElement);
      builder.addStatement("$T.createView(db, $T.$L, $S)",
          SQL_UTIL,
          viewDao,
          FIELD_VIEW_QUERY,
          viewElement.getViewName());
    }
    return builder.build();
  }

  private MethodSpec clearData(Environment environment, String className) {
    final MethodSpec.Builder method = createMagicInvokableMethod(className, METHOD_CLEAR_DATA)
        .returns(STRING_ARRAY_SET)
        .addAnnotation(NON_NULL);
    final CodeBlock.Builder body = CodeBlock.builder();
    body.addStatement("final $1T allChangedTables = new $1T($2L(null))",
        STRING_ARRAY_SET, METHOD_GET_NR_OF_TABLES);
    addSubmodulesClearDataIfNeeded(environment, body);
    addDebugLogging(body, "Clearing data");
    final CodeBlock.Builder resultBuilder = CodeBlock.builder()
        .add("allChangedTables.addAll(new String[]{");
    boolean firstTime = true;
    for (TableElement tableElement : environment.getAllTableElements()) {
      final String tableName = tableElement.getTableName();
      body.addStatement("db.execSQL($S)", "DELETE FROM " + tableName);
      if (firstTime) {
        firstTime = false;
      } else {
        resultBuilder.add(", ");
      }
      resultBuilder.add("$S", tableName);
    }
    resultBuilder.add("});\n");
    resultBuilder.addStatement("return allChangedTables");
    return WriterUtil.buildSqlTransactionMethod(method, body.build(), resultBuilder.build(), true)
        .toBuilder()
        .build();
  }

  private void addSubmodulesClearDataIfNeeded(Environment environment, final CodeBlock.Builder body) {
    forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
      @Override
      public void call(TypeMirror type, String moduleName) {
        body.addStatement("allChangedTables.addAll($T.$L(db))",
            type,
            METHOD_CLEAR_DATA);
      }
    });
  }

  private MethodSpec submoduleNames(Environment environment, String className) {
    final MethodSpec.Builder builder = createMagicInvokableMethod(className, METHOD_GET_SUBMODULE_NAMES)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addAnnotation(NULLABLE)
        .returns(String[].class);
    if (!environment.hasSubmodules()) {
      builder.addStatement("return null");
    } else {
      builder.addCode("return new String[] {");
      forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
        @Override
        public void call(TypeMirror type, String moduleName) {
          builder.addCode("$S", moduleName);
        }
      });
      builder.addCode("};\n");
    }
    return builder.build();
  }

  private MethodSpec nrOfTables(Environment environment, String className) {
    final MethodSpec.Builder builder = createMagicInvokableMethod(className, METHOD_GET_NR_OF_TABLES)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(nullableParameter(STRING, MODULE_NAME_VARIABLE))
        .returns(TypeName.INT);
    final boolean hasSubmodules = environment.hasSubmodules();
    final CodeBlock.Builder resultBuilder = CodeBlock.builder();
    if (hasSubmodules) {
      resultBuilder.beginControlFlow("if ($L == null)", MODULE_NAME_VARIABLE);
    }
    final int moduleNrOfTables = environment.getAllTableElements().size();
    resultBuilder.add("return $L", moduleNrOfTables);
    forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
      @Override
      public void call(TypeMirror type, String moduleName) {
        resultBuilder.add(" + $T.$L(null)",
            type,
            METHOD_GET_NR_OF_TABLES);
      }
    });
    resultBuilder.add(";\n");
    if (hasSubmodules) {
      resultBuilder.endControlFlow();
      resultBuilder.beginControlFlow("switch ($L)", MODULE_NAME_VARIABLE);
      final CodeBlock.Builder switchBody = CodeBlock.builder();
      forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
        @Override
        public void call(TypeMirror type, String moduleName) {
          switchBody.add("case $S:\n", moduleName)
              .indent()
              .addStatement("return $T.$L($L)",
                  type,
                  METHOD_GET_NR_OF_TABLES,
                  MODULE_NAME_VARIABLE);
          switchBody.unindent();
        }
      });
      switchBody.add("default:\n")
          .indent()
          .addStatement("return $L", moduleNrOfTables)
          .unindent();
      resultBuilder.add(switchBody.build());
      resultBuilder.endControlFlow();
    }
    return builder
        .addCode(resultBuilder.build())
        .build();
  }

  private MethodSpec dbVersion(Environment environment, String className) {
    final Integer dbVersion = environment.getDbVersion();
    return createMagicInvokableMethod(className, METHOD_GET_DB_VERSION)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .returns(TypeName.INT)
        .addStatement("return $L", (dbVersion != null) ? dbVersion : 1)
        .build();
  }

  private MethodSpec dbName(Environment environment, String className) {
    return createMagicInvokableMethod(className, METHOD_GET_DB_NAME)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .returns(String.class)
        .addStatement("return $L", environment.getDbName())
        .build();
  }

  private MethodSpec columnForValue(Environment environment, GenClassesManagerStep managerStep, String className) {
    final TypeVariableName valType = TypeVariableName.get("V");
    final ParameterizedTypeName returnType = ParameterizedTypeName.get(COLUMN,
        valType, valType, valType,
        anyWildcardTypeName(), NOT_NULLABLE_COLUMN);
    final MethodSpec.Builder builder = createMagicInvokableMethod(className, METHOD_COLUMN_FOR_VALUE)
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
    final CodeBlock.Builder switchBody = columnForValueSwitchBody(environment, managerStep);
    switchBody.add("default:\n")
        .indent();
    forEachSubmoduleDatabase(environment, new Callback2<TypeMirror, String>() {
      int i = 0;

      @Override
      public void call(TypeMirror type, String moduleName) {
        final String tmpVariableName = "_" + i;
        switchBody.addStatement("final $T $L = $T.$L(className, $L)",
            returnType,
            tmpVariableName,
            type,
            METHOD_COLUMN_FOR_VALUE_OR_NULL,
            VAL_VARIABLE)
            .beginControlFlow("if ($L != null)", tmpVariableName)
            .addStatement("return $L", tmpVariableName)
            .endControlFlow();
        i++;
      }
    });
    switchBody.addStatement("return new $T<>($T.ANONYMOUS_TABLE, \"'\" + val.toString() + \"'\", false, $T.STRING_PARSER, false, null)",
        COLUMN,
        TABLE,
        UTIL)
        .unindent();
    builder.addCode(switchBody.build())
        .endControlFlow();
    return builder.build();
  }

  private MethodSpec columnForValueOrNull(Environment environment, GenClassesManagerStep managerStep, String className) {
    final TypeVariableName valType = TypeVariableName.get("V");
    final ParameterizedTypeName returnType = ParameterizedTypeName.get(COLUMN,
        valType, valType, valType,
        anyWildcardTypeName(), NOT_NULLABLE_COLUMN);
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_COLUMN_FOR_VALUE_OR_NULL)
        .addTypeVariable(valType)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addAnnotation(NULLABLE)
        .addParameter(notNullParameter(STRING, "className"))
        .addParameter(notNullParameter(valType, VAL_VARIABLE))
        .returns(returnType)
        .addStatement("final $T strVal", STRING)
        .beginControlFlow("switch (className)");
    final CodeBlock.Builder switchBody = columnForValueSwitchBody(environment, managerStep);
    switchBody.add("default:\n")
        .indent()
        .addStatement("return null")
        .unindent();
    builder.addCode(switchBody.build())
        .endControlFlow();
    return builder.build();
  }

  private CodeBlock.Builder columnForValueSwitchBody(Environment environment, GenClassesManagerStep managerStep) {
    int i = 0;
    final boolean submodule = environment.isSubmodule();
    final Collection<TransformerElement> transformers = managerStep.getAllTransformerElements().values();
    final Map<String, Integer> transformerRepetitions = findTransformerRepetitions(transformers);
    final Set<String> handledTransformers = new HashSet<>(transformers.size());
    final CodeBlock.Builder switchBody = CodeBlock.builder();
    for (TransformerElement transformer : transformers) {
      if (submodule && transformer.isDefaultTransformer()) {
        continue;
      }
      final String qualifiedDeserializedName = transformer.getQualifiedDeserializedName();
      if (handledTransformers.contains(qualifiedDeserializedName)) {
        continue;
      }
      handledTransformers.add(qualifiedDeserializedName);
      switchBody.add("case $S:\n", qualifiedDeserializedName)
          .indent();
      final Integer repetitionCount = transformerRepetitions.get(qualifiedDeserializedName);
      if (repetitionCount > 1) {
        switchBody.addStatement("throw new $T($S)",
            UnsupportedOperationException.class,
            "Unable to disambiguate transformer for " + qualifiedDeserializedName)
            .unindent();
        continue;
      }
      final String objValName = "objVal" + i;
      switchBody.addStatement("final $1T $2L = ($1T) $3L",
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
      switchBody.addStatement("return new $T($T.ANONYMOUS_TABLE, strVal, $T.$L, false, null)",
          ClassName.get(PACKAGE_ROOT, ColumnClassWriter.getClassName(transformer)),
          TABLE,
          UTIL,
          transformer.cursorParserConstantName(environment, false))
          .unindent();
      i++;
    }
    return switchBody;
  }

  private static Map<String, Integer> findTransformerRepetitions(Collection<TransformerElement> transformers) {
    final HashMap<String, Integer> transformerRepetitions = new HashMap<>(transformers.size());
    for (TransformerElement transformer : transformers) {
      final String qualifiedDeserializedName = transformer.getQualifiedDeserializedName();
      final Integer repetitionCount = transformerRepetitions.get(qualifiedDeserializedName);
      if (repetitionCount != null) {
        transformerRepetitions.put(qualifiedDeserializedName, repetitionCount + 1);
      } else {
        transformerRepetitions.put(qualifiedDeserializedName, 1);
      }
    }
    return transformerRepetitions;
  }

  static ParameterSpec columnsParam() {
    return ParameterSpec.builder(ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, STRING, TypeName.INT.box()),
        "columns")
        .addAnnotation(NULLABLE)
        .addModifiers(Modifier.FINAL)
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
        .addModifiers(Modifier.FINAL)
        .build();
  }

  static ParameterSpec select1Param() {
    return ParameterSpec.builder(TypeName.BOOLEAN,
        "select1")
        .build();
  }

  private static void forEachSubmoduleDatabase(Environment environment, Callback2<TypeMirror, String> callback) {
    final List<Dual<TypeElement, String>> submoduleDatabases = environment.getSubmoduleDatabases();
    if (submoduleDatabases != null && !submoduleDatabases.isEmpty()) {
      for (Dual<TypeElement, String> element : submoduleDatabases) {
        callback.call(element.getFirst().asType(), element.getSecond());
      }
    }
  }
}
