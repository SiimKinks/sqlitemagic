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
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.Const.STATEMENT_METHOD_MAP;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.SqliteMagicProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.WriterUtil.CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.TRANSACTION;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.dbVariableFromPresentConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.ifNotSubscriberUnsubscribed;
import static com.siimkinks.sqlitemagic.WriterUtil.subscriberOnCompleted;
import static com.siimkinks.sqlitemagic.WriterUtil.subscriberOnError;
import static com.siimkinks.sqlitemagic.WriterUtil.subscriberOnSuccess;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_INSERT_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_TABLE_SCHEMA;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_UPDATE_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_CONTENT_VALUES_EXCEPT_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_NOT_NULL_CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_ID;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;

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
    if (tableElement.getIdColumn().isAutoincrementId()) {
      daoClassBuilder.addMethod(bindAllExceptIdToContentValues(entityEnvironment));
    }
    if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
      daoClassBuilder.addMethod(bindAllToContentValuesWithImmutableComplexColumns(entityEnvironment));
    }
    daoClassBuilder.addMethod(bindToNotNullContentValues(entityEnvironment))
        .addMethod(bindAllToContentValues(entityEnvironment));
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

  private MethodSpec bindAllToContentValues(EntityEnvironment entityEnvironment) {
    CodeBlock.Builder valuesGatherBlock = buildAllValuesGatheringBlock(entityEnvironment.getTableElement());
    return bindToContentValues(entityEnvironment, METHOD_BIND_TO_CONTENT_VALUES, valuesGatherBlock).build();
  }

  private MethodSpec bindAllToContentValuesWithImmutableComplexColumns(EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    CodeBlock.Builder valuesGatherBlock = buildAllValuesGatheringBlockWithImmutableComplexColumns(tableElement.getAllColumns());
    final MethodSpec.Builder builder = bindToContentValues(entityEnvironment, METHOD_BIND_TO_CONTENT_VALUES, valuesGatherBlock);
    addImmutableIdsParameterIfNeeded(builder, tableElement);
    return builder.build();
  }

  private MethodSpec bindAllExceptIdToContentValues(EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    CodeBlock.Builder valuesGatherBlock = buildAllValuesGatheringBlockWithImmutableComplexColumns(tableElement.getColumnsExceptId());
    final MethodSpec.Builder builder = bindToContentValues(entityEnvironment, METHOD_BIND_TO_CONTENT_VALUES_EXCEPT_ID, valuesGatherBlock);
    addImmutableIdsParameterIfNeeded(builder, tableElement);
    return builder.build();
  }

  private MethodSpec bindToNotNullContentValues(EntityEnvironment entityEnvironment) {
    final CodeBlock.Builder valuesGatherBlock = buildNotNullValuesGatheringBlock(entityEnvironment.getTableElement());
    MethodSpec.Builder builder = bindToContentValues(entityEnvironment, METHOD_BIND_TO_NOT_NULL_CONTENT_VALUES, valuesGatherBlock);
    addImmutableIdsParameterIfNeeded(builder, entityEnvironment.getTableElement());
    return builder.build();
  }

  private MethodSpec.Builder bindToContentValues(EntityEnvironment entityEnvironment, String methodName, CodeBlock.Builder valuesGatherBlock) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityEnvironment.getTableElementTypeName(), ENTITY_VARIABLE)
        .addParameter(CONTENT_VALUES, "values");
    builder.addStatement("values.clear()")
        .addCode(valuesGatherBlock.build());
    return builder;
  }

  private CodeBlock.Builder buildAllValuesGatheringBlock(TableElement tableElement) {
    final CodeBlock.Builder valuesGatherBlock = CodeBlock.builder();
    for (ColumnElement columnElement : tableElement.getAllColumns()) {
      addBindToValuesBlock(valuesGatherBlock, columnElement);
    }
    return valuesGatherBlock;
  }

  private CodeBlock.Builder buildAllValuesGatheringBlockWithImmutableComplexColumns(List<ColumnElement> columns) {
    final CodeBlock.Builder valuesGatherBlock = CodeBlock.builder();
    int immutableIdColPos = 0;
    for (ColumnElement columnElement : columns) {
      if (columnElement.isHandledRecursively() && columnElement.isReferencedTableImmutable()) {
        addBindFromProvidedIdsToContentValues(valuesGatherBlock, immutableIdColPos, columnElement);
        immutableIdColPos++;
      } else {
        addBindToValuesBlock(valuesGatherBlock, columnElement);
      }
    }
    return valuesGatherBlock;
  }

  private void addBindToValuesBlock(CodeBlock.Builder valuesGatherBlock, ColumnElement columnElement) {
    if (columnElement.isReferencedColumn() && columnElement.isNullable()) {
      final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
      valuesGatherBlock.beginControlFlow("if ($L != null)", valueGetter);
      addPutToValuesBlock(valuesGatherBlock, columnElement);
      valuesGatherBlock.endControlFlow();
    } else {
      addPutToValuesBlock(valuesGatherBlock, columnElement);
    }
  }

  private void addPutToValuesBlock(CodeBlock.Builder valuesGatherBlock, ColumnElement columnElement) {
    FormatData serializedValueGetter = columnElement.serializedValueGetterFromEntity(ENTITY_VARIABLE);
    valuesGatherBlock.addStatement(String.format("values.put($S, %s)", serializedValueGetter.getFormat()),
        serializedValueGetter.getWithOtherArgsBefore(columnElement.getColumnName()));
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

  static final ReturnCallback2<String, ParameterSpec, ColumnElement> COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER = new ReturnCallback2<String, ParameterSpec, ColumnElement>() {
    @Override
    public String call(ParameterSpec param, ColumnElement columnElement) {
      return param.name + ".getEntityDbManager(" + columnElement.getReferencedTable().getTablePos() + ")";
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

  static void addInlineNullCheck(MethodSpec.Builder builder, FormatData getter, String errMsg) {
    builder.beginControlFlow(String.format("if (%s == null)", getter.getFormat()), getter.getArgs())
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

  static void addInlineExecuteInsertWithCheckIdValidity(MethodSpec.Builder builder, String insertStmVariableName, String errMsg) {
    builder.beginControlFlow("if ($L.executeInsert() == -1)", insertStmVariableName);
    addThrowOperationFailedExceptionWithEntityVariable(builder, errMsg);
    builder.endControlFlow();
  }

  static void addContentValuesAndDbVariables(MethodSpec.Builder builder) {
    builder.addStatement("final $T values = new $T()", CONTENT_VALUES, CONTENT_VALUES)
        .addCode(dbVariableFromPresentConnectionVariable());
  }

  static CodeBlock contentValuesAndDbVariables() {
    return CodeBlock.builder()
        .addStatement("final $T values = new $T()", CONTENT_VALUES, CONTENT_VALUES)
        .add(dbVariableFromPresentConnectionVariable())
        .build();
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
    builder.addStatement("throw new $T(String.format(\"$L %s\", $L.toString()))", OPERATION_FAILED_EXCEPTION, errMsg, ENTITY_VARIABLE);
  }

  static void addTopMethodEndBlock(@NonNull MethodSpec.Builder builder, @NonNull Set<TableElement> allTableTriggers, boolean hasComplexColumns,
                                   @NonNull String returnStatement, @NonNull String failReturnStatement) {
    addTopMethodEndBlock(builder, allTableTriggers, hasComplexColumns, CodeBlock.builder().addStatement(returnStatement).build(), failReturnStatement);
  }

  static void addTopMethodEndBlock(MethodSpec.Builder builder, Set<TableElement> allTableTriggers, boolean hasComplexColumns,
                                   CodeBlock returnStatement, String failReturnStatement) {
    if (hasComplexColumns) {
      addTransactionEndBlock(builder, allTableTriggers, returnStatement, failReturnStatement);
    } else {
      addTableTriggersSendingStatement(builder, allTableTriggers);
      builder.addCode(returnStatement)
          .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION)
          .addStatement(failReturnStatement)
          .endControlFlow();
    }
  }

  public static void addTransactionStartBlock(MethodSpec.Builder builder) {
    builder.addStatement("final $T $L = $L.newTransaction()",
        TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
        .addStatement("boolean success = false")
        .beginControlFlow("try");
  }

  static void addTransactionEndBlock(@NonNull MethodSpec.Builder builder, @NonNull Set<TableElement> allTableTriggers,
                                     @NonNull String returnStatement, @NonNull String failReturnStatement) {
    addTransactionEndBlock(builder, allTableTriggers, CodeBlock.builder().addStatement(returnStatement).build(), failReturnStatement);
  }

  static void addTransactionEndBlock(@NonNull MethodSpec.Builder builder, @NonNull Set<TableElement> allTableTriggers,
                                     @NonNull CodeBlock returnStatement, @NonNull String failReturnStatement) {
    builder.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("success = true")
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
    builder.endControlFlow()
        .endControlFlow();
  }

  public static void addRxObservableTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                                        @NonNull Set<TableElement> allTableTriggers) {
    builder.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("success = true")
        .nextControlFlow("catch ($T e)", Throwable.class)
        .beginControlFlow(ifNotSubscriberUnsubscribed())
        .addStatement(subscriberOnError())
        .endControlFlow()
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .beginControlFlow("if (success)")
        .beginControlFlow(ifNotSubscriberUnsubscribed())
        .addStatement(subscriberOnCompleted())
        .endControlFlow();
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.endControlFlow()
        .endControlFlow();
  }

  public static void addRxSingleTransactionEndBlock(@NonNull MethodSpec.Builder builder,
                                                    @NonNull Set<TableElement> allTableTriggers,
                                                    @NonNull String successValue) {
    builder
        .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("success = true")
        .nextControlFlow("catch ($T e)", Throwable.class)
        .beginControlFlow(ifNotSubscriberUnsubscribed())
        .addStatement(subscriberOnError())
        .endControlFlow()
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .beginControlFlow("if (success)")
        .beginControlFlow(ifNotSubscriberUnsubscribed())
        .addStatement(subscriberOnSuccess(successValue))
        .endControlFlow();
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.endControlFlow()
        .endControlFlow();
  }

  static void addCallToComplexColumnsOperationWithContentValuesIfNeeded(MethodSpec.Builder builder,
                                                                        EntityEnvironment entityEnvironment,
                                                                        String complexColumnsOperationMethodName,
                                                                        String... params) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    String extraParamsToInternalMethodCall = "";
    for (String param : params) {
      extraParamsToInternalMethodCall += ", " + param;
    }
    final CodeBlock.Builder statementBuilder = CodeBlock.builder();
    if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
      statementBuilder.add("final long[] ids = ");
    }
    if (tableElement.hasAnyPersistedComplexColumns()) {
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
    insertSql.append("INSERT INTO ");
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
    updateSql.append("UPDATE OR ABORT ")
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
