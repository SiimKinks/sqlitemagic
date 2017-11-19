package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.NonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback2;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.ReturnCallback;
import com.siimkinks.sqlitemagic.util.ReturnCallback2;
import com.siimkinks.sqlitemagic.util.StringUtil;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.siimkinks.sqlitemagic.BaseProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.Const.STATEMENT_METHOD_MAP;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_UNSUBSCRIBED_UNEXPECTEDLY;
import static com.siimkinks.sqlitemagic.WriterUtil.BIND_VALUES_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.DISPOSABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.DISPOSABLES;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.SQL_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING;
import static com.siimkinks.sqlitemagic.WriterUtil.TRANSACTION;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnComplete;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnError;
import static com.siimkinks.sqlitemagic.WriterUtil.ifDisposed;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_INSERT_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_TABLE_SCHEMA;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_UPDATE_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_NOT_NULL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_UNIQUE_COLUMN;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_IS_UNIQUE_COLUMN_NULL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_ID;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DISPOSABLE_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.EMITTER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_BY_COLUMNS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_HELPER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.UPDATE_BY_COLUMN_VARIABLE;

// FIXME !!! check logging generation
public class ModelPersistingGenerator implements ModelPartGenerator {

  @Override
  public void write(TypeSpec.Builder daoClassBuilder, TypeSpec.Builder handlerClassBuilder, EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    final Set<TableElement> allTableTriggers = tableElement.getAllTableTriggers();

    final InsertWriter insertWriter = InsertWriter.create(entityEnvironment, allTableTriggers);
    final OperationWriter[] writers = new OperationWriter[]{
        insertWriter,
        UpdateWriter.create(entityEnvironment, allTableTriggers),
        PersistWriter.create(entityEnvironment, allTableTriggers, insertWriter)
    };

    writeDao(daoClassBuilder, entityEnvironment);
    writeHandler(handlerClassBuilder, entityEnvironment);

    for (OperationWriter writer : writers) {
      writer.writeDao(daoClassBuilder);
      writer.writeHandler(handlerClassBuilder);
    }
  }

  public void writeDao(TypeSpec.Builder daoClassBuilder, EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    if (tableElement.hasIdSetter()) {
      daoClassBuilder.addMethod(entityEnvironment.getEntityIdSetter());
    }
    daoClassBuilder.addMethod(bindToNotNullContentValues(entityEnvironment));
    if (tableElement.hasUniqueColumnsOtherThanId()) {
      final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
      daoClassBuilder.addMethod(bindUniqueColumn(tableElement, tableElementTypeName));
      if (tableElement.isAnyUniqueColumnNullable()) {
        daoClassBuilder.addMethod(isUniqueColumnNull(tableElement, tableElementTypeName));
      }
    }
  }

  public void writeHandler(TypeSpec.Builder handlerClassBuilder, EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    handlerClassBuilder.addField(schema(tableElement))
        .addField(insertSqlField(tableElement))
        .addField(updateSqlField(tableElement));
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private MethodSpec isUniqueColumnNull(TableElement tableElement, TypeName tableElementTypeName) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_IS_UNIQUE_COLUMN_NULL)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(STRING, "columnName")
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .returns(TypeName.BOOLEAN)
        .beginControlFlow("switch (columnName)");
    for (ColumnElement column : tableElement.getAllColumns()) {
      if (column.isUnique() || column.isId()) {
        builder.addCode("case $S:\n", column.getColumnName());
        final boolean columnNullable = column.isNullable();
        if (columnNullable) {
          if (column.isReferencedColumn() && column.getReferencedTable().getIdColumn().isNullable()) {
            final TableElement referencedTable = column.getReferencedTable();
            final String valueGetter = column.valueGetter(ENTITY_VARIABLE);
            final String variableName = column.getElementName();
            builder.addStatement("final $T $L = $L",
                referencedTable.getTableElementTypeName(),
                variableName,
                valueGetter);
            final FormatData idGetterFromDao = EntityEnvironment.idGetterFromDaoIfNeeded(column, variableName);
            builder.beginControlFlow(idGetterFromDao.formatInto("return $L == null || %s == null"),
                idGetterFromDao.getWithOtherArgsBefore(variableName));
          } else {
            final String valueGetter = column.valueGetter(ENTITY_VARIABLE);
            builder.addStatement("return $L == null", valueGetter);
          }
        } else {
          builder.addStatement("return false");
        }
      }
    }
    builder.endControlFlow();
    builder.addStatement("throw new $T(\"Column \" + columnName + \" is not unique\")", IllegalStateException.class);
    return builder.build();
  }

  private MethodSpec bindUniqueColumn(TableElement tableElement, TypeName tableElementTypeName) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_UNIQUE_COLUMN)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(SQLITE_STATEMENT, "statement")
        .addParameter(TypeName.INT, "index")
        .addParameter(STRING, "columnName")
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .beginControlFlow("switch (columnName)");
    for (ColumnElement column : tableElement.getAllColumns()) {
      if (column.isUnique() || column.isId()) {
        builder
            .addCode("case $S:\n", column.getColumnName())
            .addCode(createBindColumnToStatement("index", column))
            .addStatement("break");
      }
    }
    builder.endControlFlow();
    return builder.build();
  }

  private MethodSpec bindToNotNullContentValues(EntityEnvironment entityEnvironment) {
    final CodeBlock.Builder valuesGatherBlock = buildNotNullValuesGatheringBlock(entityEnvironment.getTableElement());
    MethodSpec.Builder builder = bindToMap(entityEnvironment, METHOD_BIND_TO_NOT_NULL, valuesGatherBlock);
    addImmutableIdsParameterIfNeeded(builder, entityEnvironment.getTableElement());
    return builder.build();
  }

  private MethodSpec.Builder bindToMap(EntityEnvironment entityEnvironment, String methodName, CodeBlock.Builder valuesGatherBlock) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityEnvironment.getTableElementTypeName(), ENTITY_VARIABLE)
        .addParameter(BIND_VALUES_MAP, "values");
    builder.addStatement("values.clear()")
        .addCode(valuesGatherBlock.build());
    return builder;
  }

  private CodeBlock.Builder buildNotNullValuesGatheringBlock(TableElement tableElement) {
    CodeBlock.Builder valuesGatherBlock = CodeBlock.builder();
    int immutableIdColPos = 0;
    for (final ColumnElement columnElement : tableElement.getAllColumns()) {
      if (columnElement.isHandledRecursively() && columnElement.isReferencedTableImmutable()) {
        addBindFromProvidedIdsToContentValues(valuesGatherBlock, immutableIdColPos, columnElement);
        immutableIdColPos++;
      } else {
        valuesGatherBlock.add(createBindBlockWithChecks(columnElement, new Callback2<CodeBlock.Builder, FormatData>() {
          @Override
          public void call(CodeBlock.Builder builder, FormatData serializedValueGetter) {
            builder.addStatement(String.format("values.put($S, %s)", serializedValueGetter.getFormat()),
                serializedValueGetter.getWithOtherArgsBefore(columnElement.getColumnName()));
          }
        }).build());
      }
    }
    return valuesGatherBlock;
  }

  private void addBindFromProvidedIdsToContentValues(CodeBlock.Builder valuesGatherBlock, int immutableIdColPos, ColumnElement columnElement) {
    if (columnElement.isNullable()) {
      valuesGatherBlock.beginControlFlow("if (ids[$L] > 0)", immutableIdColPos);
    }
    valuesGatherBlock.addStatement("values.put($S, ids[$L])", columnElement.getColumnName(), immutableIdColPos);
    if (columnElement.isNullable()) {
      valuesGatherBlock.endControlFlow();
    }
  }

  static void addBindColumnFromProvidedIdsBlock(MethodSpec.Builder builder, ColumnElement columnElement, int colPos, int immutableIdColPos) {
    final String bindMethod = STATEMENT_METHOD_MAP.get(columnElement.getSerializedType().getQualifiedName());
    if (columnElement.isNullable()) {
      builder.beginControlFlow("if (ids[$L] > 0)", immutableIdColPos);
    }
    builder.addStatement("statement.$L($L, ids[$L])", bindMethod, colPos, immutableIdColPos);
    if (columnElement.isNullable()) {
      builder.endControlFlow();
    }
  }

  static void addBindColumnToStatementBlock(MethodSpec.Builder builder, final int colPos, final ColumnElement columnElement) {
    builder.addCode(createBindBlockWithChecks(columnElement, new Callback2<CodeBlock.Builder, FormatData>() {
      @Override
      public void call(CodeBlock.Builder builder, FormatData serializedValueGetter) {
        final String bindMethod = STATEMENT_METHOD_MAP.get(columnElement.getSerializedType().getQualifiedName());
        builder.addStatement(String.format("statement.$L($L, %s)", serializedValueGetter.getFormat()),
            serializedValueGetter.getWithOtherArgsBefore(bindMethod, colPos));
      }
    }).build());
  }

  static CodeBlock.Builder createBindBlockWithChecks(ColumnElement columnElement, Callback2<CodeBlock.Builder, FormatData> realBindAddingCallback) {
    CodeBlock.Builder builder = CodeBlock.builder();
    final boolean columnNullable = columnElement.isNullable();
    if (columnElement.isReferencedColumn()) {
      final TableElement referencedTable = columnElement.getReferencedTable();
      final ColumnElement referencedTableIdColumn = referencedTable.getIdColumn();
      final boolean referencedIdColumnNullable = referencedTableIdColumn.isNullable();
      if (!columnNullable && !referencedIdColumnNullable) {
        realBindAddingCallback.call(builder, columnElement.serializedValueGetterFromEntity(ENTITY_VARIABLE));
      } else {
        final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
        final String variableName = columnElement.getElementName();
        builder.addStatement("final $T $L = $L",
            referencedTable.getTableElementTypeName(),
            variableName,
            valueGetter);
        final FormatData idGetterFromDao = EntityEnvironment.idGetterFromDaoIfNeeded(columnElement, variableName);
        if (columnNullable && referencedIdColumnNullable) {
          builder.beginControlFlow(idGetterFromDao.formatInto("if ($L != null && %s != null)"),
              idGetterFromDao.getWithOtherArgsBefore(variableName));
        } else if (columnNullable) {
          builder.beginControlFlow("if ($L != null)", variableName);
        } else {
          builder.beginControlFlow(idGetterFromDao.formatInto("if (%s != null)"), idGetterFromDao.getArgs());
        }
        realBindAddingCallback.call(builder, idGetterFromDao);
        builder.endControlFlow();
      }
    } else {
      final FormatData serializedValueGetter = columnElement.serializedValueGetterFromEntity(ENTITY_VARIABLE);
      if (!columnNullable) {
        realBindAddingCallback.call(builder, serializedValueGetter);
      } else {
        final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
        builder.beginControlFlow("if ($L != null)", valueGetter);
        realBindAddingCallback.call(builder, serializedValueGetter);
        builder.endControlFlow();
      }
    }
    return builder;
  }

  static CodeBlock createBindColumnToStatement(final String columnIndexVariable,
                                               final ColumnElement columnElement) {
    final Callback2<CodeBlock.Builder, FormatData> bindFunction = new Callback2<CodeBlock.Builder, FormatData>() {
      @Override
      public void call(CodeBlock.Builder builder, FormatData serializedValueGetter) {
        final String bindMethod = STATEMENT_METHOD_MAP.get(columnElement.getSerializedType().getQualifiedName());
        builder.addStatement(String.format("statement.$L($L, %s)", serializedValueGetter.getFormat()),
            serializedValueGetter.getWithOtherArgsBefore(bindMethod, columnIndexVariable));
      }
    };

    final CodeBlock.Builder builder = CodeBlock.builder();
    final boolean columnNullable = columnElement.isNullable();
    if (columnElement.isReferencedColumn()) {
      final TableElement referencedTable = columnElement.getReferencedTable();
      final ColumnElement referencedTableIdColumn = referencedTable.getIdColumn();
      final boolean referencedIdColumnNullable = referencedTableIdColumn.isNullable();
      if (!columnNullable && !referencedIdColumnNullable) {
        bindFunction.call(builder, columnElement.serializedValueGetterFromEntity(ENTITY_VARIABLE));
      } else {
        final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
        final String variableName = columnElement.getElementName();
        builder.addStatement("final $T $L = $L",
            referencedTable.getTableElementTypeName(),
            variableName,
            valueGetter);
        final FormatData idGetterFromDao = EntityEnvironment.idGetterFromDaoIfNeeded(columnElement, variableName);
        if (columnNullable && referencedIdColumnNullable) {
          builder.beginControlFlow(idGetterFromDao.formatInto("if ($L == null || %s == null)"),
              idGetterFromDao.getWithOtherArgsBefore(variableName));
        } else if (columnNullable) {
          builder.beginControlFlow("if ($L == null)", variableName);
        } else {
          builder.beginControlFlow(idGetterFromDao.formatInto("if (%s == null)"), idGetterFromDao.getArgs());
        }
        builder.addStatement("throw new $T(\"$L column is null\")",
            NullPointerException.class, columnElement.getColumnName());
        builder.endControlFlow();
        bindFunction.call(builder, idGetterFromDao);
      }
    } else {
      final FormatData serializedValueGetter = columnElement.serializedValueGetterFromEntity(ENTITY_VARIABLE);
      if (!columnNullable) {
        bindFunction.call(builder, serializedValueGetter);
      } else {
        final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
        builder.beginControlFlow("if ($L == null)", valueGetter)
            .addStatement("throw new $T(\"$L column is null\")",
                NullPointerException.class, columnElement.getColumnName())
            .endControlFlow();
        bindFunction.call(builder, serializedValueGetter);
      }
    }
    return builder.build();
  }

  static CodeBlock updateByColumnVariable(TableElement tableElement) {
    return CodeBlock.builder()
        .addStatement("final $T column = $T.firstColumnForTable($S, $L)",
            COLUMN,
            SQL_UTIL,
            tableElement.getTableName(),
            OPERATION_BY_COLUMNS_VARIABLE)
        .addStatement("final $T $L", STRING, UPDATE_BY_COLUMN_VARIABLE)
        .beginControlFlow("if (column != null)")
        .addStatement("$L = column.name", UPDATE_BY_COLUMN_VARIABLE)
        .nextControlFlow("else")
        .addStatement("$L = $S", UPDATE_BY_COLUMN_VARIABLE, tableElement.getIdColumn().getColumnName())
        .endControlFlow()
        .build();
  }

  static final ReturnCallback2<String, ParameterSpec, ColumnElement> COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER = new ReturnCallback2<String, ParameterSpec, ColumnElement>() {
    @Override
    public String call(ParameterSpec param, ColumnElement columnElement) {
      if (DB_CONNECTION_VARIABLE.equals(param.name)) {
        return param.name + ".getEntityDbManager(" + columnElement.getReferencedTable().getTablePos() + ")";
      }
      return param.name;
    }
  };

  static void addMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder,
                                                            EntityEnvironment entityEnvironment,
                                                            String methodName,
                                                            ReturnCallback<String, ColumnElement> callableMethodCallback,
                                                            ParameterSpec... params) {
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, methodName,
        new ReturnCallback2<String, ParameterSpec, ColumnElement>() {
          @Override
          public String call(ParameterSpec param, ColumnElement columnElement) {
            return param.name;
          }
        },
        callableMethodCallback, params);
  }

  static void addMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder,
                                                            EntityEnvironment entityEnvironment,
                                                            String methodName,
                                                            ReturnCallback2<String, ParameterSpec, ColumnElement> paramEval,
                                                            ReturnCallback<String, ColumnElement> callableMethodCallback,
                                                            ParameterSpec... params) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    if (tableElement.hasAnyPersistedComplexColumns()) {
      MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
          .addModifiers(STATIC_METHOD_MODIFIERS)
          .addParameter(entityEnvironment.getTableElementTypeName(), ENTITY_VARIABLE);
      for (ParameterSpec param : params) {
        methodBuilder.addParameter(param);
      }
      boolean hasAnyPersistedImmutableComplexColumns = tableElement.hasAnyPersistedImmutableComplexColumns();
      if (hasAnyPersistedImmutableComplexColumns) {
        methodBuilder.returns(long[].class)
            .addStatement("final long[] ids = new long[$L]", tableElement.getPersistedImmutableComplexColumnCount());
      }
      int pos = 0;
      for (ColumnElement columnElement : tableElement.getAllColumns()) {
        if (columnElement.isHandledRecursively()) {
          final String callableMethodName = callableMethodCallback.call(columnElement);
          final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
          final ClassName referencedModelHandler = EntityEnvironment.getGeneratedHandlerClassName(columnElement.getReferencedTable());
          if (columnElement.isNullable()) {
            methodBuilder.beginControlFlow("if ($L != null)", valueGetter);
          }
          final StringBuilder sb = new StringBuilder();
          for (ParameterSpec param : params) {
            sb.append(", ")
                .append(paramEval.call(param, columnElement));
          }
          if (columnElement.isReferencedTableImmutable()) {
            methodBuilder.addStatement("ids[$L] = $T.$L($L$L)",
                pos, referencedModelHandler, callableMethodName, valueGetter, sb.toString());
          } else {
            methodBuilder.addStatement("$T.$L($L$L)",
                referencedModelHandler, callableMethodName, valueGetter, sb.toString());
          }
          if (columnElement.isNullable()) {
            methodBuilder.endControlFlow();
          }
          if (columnElement.isReferencedTableImmutable()) {
            pos++;
          }
        }
      }
      if (hasAnyPersistedImmutableComplexColumns) {
        methodBuilder.addStatement("return ids");
      }
      daoClassBuilder.addMethod(methodBuilder.build());
    }
  }

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private FieldSpec schema(TableElement tableElement) {
    List<String> columnDefinitions = new ArrayList<>();
    for (ColumnElement columnElement : tableElement.getAllColumns()) {
      String columnSchema = columnElement.getSchema();
      if (!Strings.isNullOrEmpty(columnSchema)) {
        columnDefinitions.add(columnSchema);
      }
    }
    return FieldSpec.builder(String.class, FIELD_TABLE_SCHEMA)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .initializer("\"CREATE TABLE IF NOT EXISTS $L ($L)\"",
            tableElement.getTableName(),
            Joiner.on(", ").join(columnDefinitions))
        .build();
  }

  static void addIdNullCheck(MethodSpec.Builder builder, String errMsg) {
    builder.beginControlFlow("if (id == null)")
        .addStatement("throw new NullPointerException($S)", errMsg)
        .endControlFlow();
  }

  static CodeBlock statementWithImmutableIdsIfNeeded(TableElement tableElement, String statement, Object... args) {
    return CodeBlock.builder()
        .add(statement, args)
        .add(tableElement.hasAnyPersistedImmutableComplexColumns() ? ", ids)" : ")")
        .add(WriterUtil.codeBlockEnd())
        .build();
  }

  static void addImmutableIdsParameterIfNeeded(MethodSpec.Builder builder, TableElement tableElement) {
    if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
      builder.addParameter(long[].class, "ids");
    }
  }

  static void addCheckIdValidity(MethodSpec.Builder builder, String errMsg) {
    builder.beginControlFlow("if (id == -1)");
    addThrowOperationFailedExceptionWithEntityVariable(builder, errMsg);
    builder.endControlFlow();
  }

  static void addIdValidityRespectingConflictAbort(MethodSpec.Builder builder,
                                                   TableElement tableElement,
                                                   ClassName generatedModelDaoClassName,
                                                   String errMsg) {
    builder.beginControlFlow("if (id == -1)")
        .beginControlFlow("if (!$L.ignoreConflict)", OPERATION_HELPER_VARIABLE);
    addThrowOperationFailedExceptionWithEntityVariable(builder, errMsg);
    builder.endControlFlow()
        .nextControlFlow("else");
    if (isIdSettingNeeded(tableElement)) {
      builder.addStatement("$T.$L($L, id)", generatedModelDaoClassName, METHOD_SET_ID, ENTITY_VARIABLE);
    }
    builder.addStatement("atLeastOneSuccess = true")
        .endControlFlow();
  }

  static void addInlineExecuteInsertWithCheckIdValidity(MethodSpec.Builder builder, String insertStmVariableName, String errMsg) {
    builder.beginControlFlow("if ($L.executeInsert() == -1)", insertStmVariableName);
    addThrowOperationFailedExceptionWithEntityVariable(builder, errMsg);
    builder.endControlFlow();
  }

  static void addSetIdStatementIfNeeded(TableElement tableElement, ClassName generatedModelDaoClassName, MethodSpec.Builder builder) {
    if (isIdSettingNeeded(tableElement)) {
      builder.addStatement("$T.$L($L, id)", generatedModelDaoClassName, METHOD_SET_ID, ENTITY_VARIABLE);
    }
  }

  static boolean isIdSettingNeeded(TableElement tableElement) {
    return !tableElement.isImmutable() && tableElement.getIdColumn().isAutoincrementId();
  }

  static void addTopMethodStartBlock(MethodSpec.Builder builder, boolean hasComplexColumns) {
    if (hasComplexColumns) {
      addTransactionStartBlock(builder);
    } else {
      builder.beginControlFlow("try");
    }
  }

  static void addThrowOperationFailedExceptionWithEntityVariable(MethodSpec.Builder builder, String errMsg) {
    builder.addStatement("throw new $T($S + $L)",
        OPERATION_FAILED_EXCEPTION,
        errMsg,
        ENTITY_VARIABLE);
  }

  static void addOperationFailedWhenDisposed(MethodSpec.Builder builder) {
    builder.beginControlFlow(ifDisposed())
        .addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, ERROR_UNSUBSCRIBED_UNEXPECTEDLY)
        .endControlFlow();
  }

  static void addTopMethodEndBlock(@NonNull MethodSpec.Builder builder,
                                   @NonNull Set<TableElement> allTableTriggers,
                                   boolean hasComplexColumns,
                                   @NonNull String returnStatement,
                                   @NonNull String failReturnStatement,
                                   boolean closeOpHelper) {
    addTopMethodEndBlock(builder,
        allTableTriggers,
        hasComplexColumns,
        CodeBlock.builder().addStatement(returnStatement).build(),
        failReturnStatement,
        closeOpHelper);
  }

  static void addTopMethodEndBlock(MethodSpec.Builder builder, Set<TableElement> allTableTriggers,
                                   boolean hasComplexColumns,
                                   CodeBlock returnStatement,
                                   String failReturnStatement,
                                   boolean closeOpHelper) {
    if (hasComplexColumns) {
      addTransactionEndBlock(builder,
          allTableTriggers,
          returnStatement,
          failReturnStatement,
          closeOpHelper);
    } else {
      addTableTriggersSendingStatement(builder, allTableTriggers);
      builder.addCode(returnStatement)
          .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION);
      addOperationFailedLoggingStatement(builder);
      builder.addStatement(failReturnStatement);
      if (closeOpHelper) {
        builder.nextControlFlow("finally")
            .addStatement("$L.close()", OPERATION_HELPER_VARIABLE);
      }
      builder.endControlFlow();
    }
  }

  public static void addTransactionStartBlock(MethodSpec.Builder builder) {
    builder.addStatement("final $T $L = $L.newTransaction()",
        TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
        .addStatement("boolean success = false")
        .beginControlFlow("try");
  }

  public static void addDisposableForEmitter(MethodSpec.Builder builder) {
    builder
        .addStatement("final $T $L = $T.empty()",
            DISPOSABLE,
            DISPOSABLE_VARIABLE,
            DISPOSABLES)
        .addStatement("$L.setDisposable($L)",
            EMITTER_VARIABLE,
            DISPOSABLE_VARIABLE);
  }

  static void addTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                     @NonNull Set<TableElement> allTableTriggers,
                                     @NonNull String returnStatement,
                                     @NonNull String failReturnStatement,
                                     boolean closeOpHelper) {
    addTransactionEndBlock(builder,
        allTableTriggers,
        CodeBlock.builder().addStatement(returnStatement).build(),
        failReturnStatement,
        closeOpHelper);
  }

  static void addTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                     @NonNull Set<TableElement> allTableTriggers,
                                     @NonNull CodeBlock returnStatement,
                                     @NonNull String failReturnStatement,
                                     boolean closeOpHelper) {
    addTransactionEndBlock(builder,
        allTableTriggers,
        CodeBlock.builder()
            .addStatement("success = true")
            .build(),
        returnStatement,
        failReturnStatement,
        closeOpHelper);
  }

  static void addTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                     @NonNull Set<TableElement> allTableTriggers,
                                     @NonNull CodeBlock successStatement,
                                     @NonNull CodeBlock returnStatement,
                                     @NonNull String failReturnStatement,
                                     boolean closeOpHelper) {
    builder.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addCode(successStatement)
        .addCode(returnStatement)
        .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION);
    addOperationFailedLoggingStatement(builder);
    if (!Strings.isNullOrEmpty(failReturnStatement)) {
      builder.addStatement(failReturnStatement);
    }
    builder.nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .beginControlFlow("if (success)");
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.endControlFlow();
    if (closeOpHelper) {
      builder.addStatement("$L.close()", OPERATION_HELPER_VARIABLE);
    }
    builder.endControlFlow();
  }

  public static void addRxCompletableEmitterTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                                                @NonNull Set<TableElement> allTableTriggers) {
    addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers,
        CodeBlock.builder()
            .addStatement("success = true")
            .build());
  }

  public static void addRxCompletableEmitterTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                                                @NonNull Set<TableElement> allTableTriggers,
                                                                @NonNull CodeBlock successStatement) {
    builder
        .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addCode(successStatement)
        .nextControlFlow("catch ($T e)", Throwable.class)
        .addCode(emitterOnError())
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .beginControlFlow("if (success)");
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.endControlFlow()
        .addStatement(emitterOnComplete())
        .endControlFlow();
  }

  static void addCallToComplexColumnsOperationWithVariableValuesIfNeeded(MethodSpec.Builder builder,
                                                                         EntityEnvironment entityEnvironment,
                                                                         String complexColumnsOperationMethodName,
                                                                         String... params) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    if (tableElement.hasAnyPersistedComplexColumns()) {
      String extraParamsToInternalMethodCall = "";
      for (String param : params) {
        extraParamsToInternalMethodCall += ", " + param;
      }
      final CodeBlock.Builder statementBuilder = CodeBlock.builder();
      if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
        statementBuilder.add("final long[] ids = ");
      }
      statementBuilder.add("$T.$L($L$L)",
          entityEnvironment.getDaoClassName(),
          complexColumnsOperationMethodName,
          ENTITY_VARIABLE,
          extraParamsToInternalMethodCall);
      statementBuilder.add(codeBlockEnd());
      builder.addCode(statementBuilder.build());
    }
  }

  private FieldSpec insertSqlField(TableElement tableElement) {
    final List<? extends ColumnElement> allColumns = tableElement.getAllColumns();
    final StringBuilder insertSql = new StringBuilder();
    insertSql.append("INSERT%s INTO ");
    insertSql.append(tableElement.getTableName());
    insertSql.append(" (");
    boolean firstTime = true;
    int columnCount = 0;
    for (ColumnElement column : allColumns) {
      if (column.isId() && column.isAutoincrementId()) {
        continue;
      }
      if (firstTime) {
        firstTime = false;
      } else {
        insertSql.append(", ");
      }
      insertSql.append(column.getColumnName());
      columnCount++;
    }
    insertSql.append(") VALUES (");
    StringUtil.append(", ", "?", columnCount, insertSql);
    insertSql.append(")");
    return FieldSpec.builder(String.class, FIELD_INSERT_SQL)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .initializer("$S", insertSql.toString())
        .build();
  }

  private FieldSpec updateSqlField(TableElement tableElement) {
    final List<ColumnElement> columnsExceptId = tableElement.getColumnsExceptId();
    final StringBuilder updateSql = new StringBuilder();
    updateSql.append("UPDATE%s ")
        .append(tableElement.getTableName())
        .append(" SET ");
    StringUtil.join(", ", columnsExceptId, updateSql, new StringUtil.AppendCallback<ColumnElement>() {
      @Override
      public void append(@NonNull StringBuilder sb, @NonNull ColumnElement column) {
        sb.append(column.getColumnName())
            .append("=?");
      }
    });
    updateSql.append(" WHERE ")
        .append(tableElement.getIdColumn().getColumnName())
        .append("=?");
    return FieldSpec.builder(String.class, FIELD_UPDATE_SQL)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .initializer("$S", updateSql.toString())
        .build();
  }

  private static void addOperationFailedLoggingStatement(MethodSpec.Builder builder) {
    if (GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logError(e, \"Operation failed\")",
          SQLITE_MAGIC, LOG_UTIL);
    }
  }
}
