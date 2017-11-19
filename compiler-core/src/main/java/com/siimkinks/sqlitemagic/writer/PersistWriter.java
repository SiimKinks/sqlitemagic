package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.ReturnCallback;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_INSERT_ERR_MSG;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_PERSIST_ERR_MSG;
import static com.siimkinks.sqlitemagic.WriterUtil.BIND_VALUES_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.CHECK_RESULT;
import static com.siimkinks.sqlitemagic.WriterUtil.DB_CONNECTION_IMPL;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_PERSIST_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_PERSIST_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.TRANSACTION;
import static com.siimkinks.sqlitemagic.WriterUtil.VARIABLE_ARGS_OPERATION_HELPER;
import static com.siimkinks.sqlitemagic.WriterUtil.addCallableToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addConflictAlgorithmToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addOperationByColumnToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableCreateFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableFromEmitterToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromCallableParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.bindValuesVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.dbConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnComplete;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnError;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerVariableFromDbConnection;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbVariablesForOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.insertStatementVariableFromOpHelper;
import static com.siimkinks.sqlitemagic.WriterUtil.opByColumnHelperVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationByColumnsParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.operationHelperParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxCompletableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.WriterUtil.updateStatementVariableFromOpHelper;
import static com.siimkinks.sqlitemagic.WriterUtil.variableArgsOpHelperVariable;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_NOT_NULL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_UNIQUE_COLUMN;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INTERNAL_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_IS_UNIQUE_COLUMN_NULL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_IGNORE_NULL_VALUES;
import static com.siimkinks.sqlitemagic.writer.InsertWriter.addAfterInsertLoggingStatement;
import static com.siimkinks.sqlitemagic.writer.InsertWriter.addBindToInsertStatement;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCallToComplexColumnsOperationWithVariableValuesIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addDisposableForEmitter;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addIdValidityRespectingConflictAbort;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addMethodInternalCallOnComplexColumnsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addOperationFailedWhenDisposed;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxCompletableEmitterTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addSetIdStatementIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.statementWithImmutableIdsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.updateByColumnVariable;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.INSERT_STATEMENT_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_BY_COLUMNS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_HELPER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.UPDATE_BY_COLUMN_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.UPDATE_STATEMENT_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.Operation.INSERT;
import static com.siimkinks.sqlitemagic.writer.Operation.PERSIST;
import static com.siimkinks.sqlitemagic.writer.Operation.UPDATE;
import static com.squareup.javapoet.TypeName.BOOLEAN;
import static com.squareup.javapoet.TypeName.LONG;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistWriter implements OperationWriter {

  public static final String IGNORE_NULL_VALUES_VARIABLE = "ignoreNullValues";
  private final EntityEnvironment entityEnvironment;
  private final TableElement tableElement;
  private final TypeName tableElementTypeName;
  private final Set<TableElement> allTableTriggers;

  private final TypeName iterable;
  private final ClassName daoClassName;

  private final InsertWriter insertWriter;

  public static PersistWriter create(EntityEnvironment entityEnvironment, Set<TableElement> allTableTriggers, InsertWriter insertWriter) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    return builder()
        .entityEnvironment(entityEnvironment)
        .tableElement(entityEnvironment.getTableElement())
        .tableElementTypeName(tableElementTypeName)
        .allTableTriggers(allTableTriggers)
        .iterable(typedIterable(tableElementTypeName))
        .daoClassName(entityEnvironment.getDaoClassName())
        .insertWriter(insertWriter)
        .build();
  }

  @Override
  public void writeDao(TypeSpec.Builder classBuilder) {
    addPersistIgnoringNullValuesMethodInternalCallOnComplexColumnsIdNeeded(classBuilder);
    addPersistMethodInternalCallOnComplexColumnsIdNeeded(classBuilder);
  }

  @Override
  public void writeHandler(TypeSpec.Builder classBuilder) {
    final MethodSpec internalPersist = internalPersist();
    final MethodSpec internalPersistIgnoringNull = internalPersistIgnoringNull();
    classBuilder
        .addMethod(internalPersistIgnoringNull)
        .addMethod(internalPersist())
        .addType(persist(internalPersist, internalPersistIgnoringNull))
        .addType(bulkPersist());
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private void addPersistIgnoringNullValuesMethodInternalCallOnComplexColumnsIdNeeded(TypeSpec.Builder daoClassBuilder) {
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS,
        COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
        new ReturnCallback<String, ColumnElement>() {
          @Override
          public String call(ColumnElement obj) {
            return METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES;
          }
        },
        ParameterSpec.builder(BIND_VALUES_MAP, "values").build(),
        ParameterSpec.builder(DB_CONNECTION_IMPL, DB_CONNECTION_VARIABLE).build(),
        ParameterSpec.builder(VARIABLE_ARGS_OPERATION_HELPER, OPERATION_HELPER_VARIABLE).build(),
        operationByColumnsParameter());
  }

  private void addPersistMethodInternalCallOnComplexColumnsIdNeeded(TypeSpec.Builder daoClassBuilder) {
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS,
        COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
        new ReturnCallback<String, ColumnElement>() {
          @Override
          public String call(ColumnElement obj) {
            return METHOD_INTERNAL_PERSIST;
          }
        },
        connectionImplParameter(),
        operationHelperParameter(),
        operationByColumnsParameter());
  }

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private TypeSpec persist(MethodSpec persist, MethodSpec persistIgnoringNull) {
    final ClassName interfaceType = ENTITY_PERSIST_BUILDER;
    final MethodSpec persistExecute = persistExecute(persist, persistIgnoringNull);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_PERSIST, interfaceType, tableElementTypeName, ENTITY_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, interfaceType);
    addOperationByColumnToOperationBuilder(builder, interfaceType);
    return builder
        .addSuperinterface(interfaceType)
        .addField(TypeName.BOOLEAN, IGNORE_NULL_VALUES_VARIABLE, Modifier.PRIVATE)
        .addMethod(setIgnoreNullValues(interfaceType))
        .addMethod(persistExecute)
        .addMethod(persistObserve(builder, persistExecute))
        .build();
  }

  private MethodSpec persistExecute(MethodSpec persist, MethodSpec persistIgnoringNull) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(LONG)
        .addCode(dbConnectionVariable());
    final boolean hasComplexColumns = tableElement.hasAnyPersistedComplexColumns();
    addTopMethodStartBlock(builder, hasComplexColumns);

    builder.addStatement("final $T id", LONG)
        .addCode(entityDbManagerVariableFromDbConnection(tableElement))
        .beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE)
        .addCode(variableArgsOpHelperVariable())
        .addCode(bindValuesVariable(tableElement))
        .addStatement("id = $N($L, values, $L, $L, $L)",
            persistIgnoringNull,
            ENTITY_VARIABLE,
            MANAGER_VARIABLE,
            OPERATION_HELPER_VARIABLE,
            OPERATION_BY_COLUMNS_VARIABLE);

    builder.nextControlFlow("else");

    builder
        .addCode(opByColumnHelperVariable(PERSIST))
        .beginControlFlow("try")
        .addStatement("id = $N($L, $L, $L, $L)",
            persist,
            ENTITY_VARIABLE,
            MANAGER_VARIABLE,
            OPERATION_HELPER_VARIABLE,
            OPERATION_BY_COLUMNS_VARIABLE)
        .nextControlFlow("finally")
        .addStatement("$L.close()", OPERATION_HELPER_VARIABLE)
        .endControlFlow();

    builder.endControlFlow();

    final String returnStatement = "return id";
    final String failReturnStatement = "return -1";
    ModelPersistingGenerator.addTopMethodEndBlock(builder,
        allTableTriggers,
        hasComplexColumns,
        returnStatement,
        failReturnStatement,
        false);
    return builder.build();
  }

  private MethodSpec persistObserve(TypeSpec.Builder typeBuilder, final MethodSpec persistExecute) {
    final TypeName entityTypeName = LONG.box();
    final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
        .addAnnotation(Override.class);
    addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addStatement("final $T id = $N()", LONG, persistExecute)
            .beginControlFlow("if (id == -1 && conflictAlgorithm != $T.CONFLICT_IGNORE)", SQLITE_DATABASE)
            .addStatement("throw new $T($S + $L)",
                OPERATION_FAILED_EXCEPTION, FAILED_TO_PERSIST_ERR_MSG, ENTITY_VARIABLE)
            .endControlFlow()
            .addStatement("return id");
      }
    });
    addRxSingleCreateFromCallableParentClass(builder);
    return builder.build();
  }

  private MethodSpec setIgnoreNullValues(TypeName returnType) {
    return MethodSpec.methodBuilder(METHOD_SET_IGNORE_NULL_VALUES)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(NON_NULL)
        .addAnnotation(CHECK_RESULT)
        .returns(returnType)
        .addStatement("this.$N = true", IGNORE_NULL_VALUES_VARIABLE)
        .addStatement("return this")
        .build();
  }

  private MethodSpec internalPersist() {
    final String updateStm = "updateStm";
    final String insertStm = "insertStm";
    final String presetId = "presetId";
    final boolean idColumnNullable = tableElement.getIdColumn().isNullable();
    final boolean hasUniqueColumnsOtherThanId = tableElement.hasUniqueColumnsOtherThanId();
    final boolean anyUniqueColumnNullable = tableElement.isAnyUniqueColumnNullable();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INTERNAL_PERSIST)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityParameter(tableElementTypeName))
        .addParameter(entityDbManagerParameter())
        .addParameter(operationHelperParameter())
        .addParameter(operationByColumnsParameter())
        .returns(LONG);
    addCallToComplexColumnsPersistIfNeeded(builder);
    addPersistLoggingStatement(builder);
    builder.addStatement("int rowsAffected = 0");
    if (hasUniqueColumnsOtherThanId) {
      builder.addCode(updateByColumnVariable(tableElement));
      if (anyUniqueColumnNullable) {
        builder.beginControlFlow("if (!$T.$L($L, $L))",
            daoClassName,
            METHOD_IS_UNIQUE_COLUMN_NULL,
            UPDATE_BY_COLUMN_VARIABLE,
            ENTITY_VARIABLE);
      }
    } else if (idColumnNullable) {
      builder.addCode(entityEnvironment.getFinalIdVariable(presetId))
          .beginControlFlow("if ($L != null)", presetId);
    }
    builder.addCode(updateStatementVariableFromOpHelper(tableElement, updateStm))
        .beginControlFlow("synchronized ($L)", updateStm);
    addBindToUpdateStatement(builder, updateStm, presetId, hasUniqueColumnsOtherThanId);
    builder.addStatement("rowsAffected = $L.executeUpdateDelete()", updateStm);
    builder.endControlFlow();
    if ((hasUniqueColumnsOtherThanId && anyUniqueColumnNullable) || idColumnNullable) {
      builder.endControlFlow();
    }
    builder.beginControlFlow("if (rowsAffected <= 0)");
    addPersistUpdateFailedLoggingStatement(builder);

    builder.addStatement("final long id")
        .addCode(insertStatementVariableFromOpHelper(tableElement, insertStm))
        .beginControlFlow("synchronized ($L)", insertStm);
    addBindToInsertStatement(builder, tableElement, daoClassName, insertStm);
    builder.addStatement("id = $L.executeInsert()", insertStm)
        .endControlFlow();
    addAfterInsertLoggingStatement(builder);
    addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
    builder.addStatement("return id");

    builder.endControlFlow();
    if (!hasUniqueColumnsOtherThanId && idColumnNullable) {
      builder.addStatement("return $L", presetId);
    } else {
      builder.addStatement("return $T.$N($L)", daoClassName, entityEnvironment.getEntityIdGetter(), ENTITY_VARIABLE);
    }
    return builder.build();
  }

  private void addBindToUpdateStatement(MethodSpec.Builder builder,
                                        String updateStmVariableName,
                                        String idVariableName,
                                        boolean hasUniqueColumnsOtherThanId) {
    final String bindMethodName = tableElement.hasAnyPersistedImmutableComplexColumns() ? METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS : METHOD_BIND_TO_UPDATE_STATEMENT;
    builder.addCode(statementWithImmutableIdsIfNeeded(tableElement,
        "$T.$L($L, $L", daoClassName, bindMethodName, updateStmVariableName, ENTITY_VARIABLE));
    if (hasUniqueColumnsOtherThanId) {
      builder.addStatement("$T.$L($L, $L, updateByColumn, $L)",
          daoClassName,
          METHOD_BIND_UNIQUE_COLUMN,
          updateStmVariableName,
          tableElement.getAllColumnsCount(),
          ENTITY_VARIABLE);
    } else {
      final CodeBlock.Builder bindIdBuilder = CodeBlock.builder()
          .add("$L.bindLong($L, ",
              updateStmVariableName,
              tableElement.getAllColumnsCount());
      if (!tableElement.getIdColumn().isNullable()) {
        entityEnvironment.addInlineIdVariable(bindIdBuilder);
      } else {
        bindIdBuilder.add("$L", idVariableName);
      }
      bindIdBuilder.add(");\n");
      builder.addCode(bindIdBuilder.build());
    }
  }

  private void addCallToComplexColumnsPersistIfNeeded(MethodSpec.Builder builder) {
    addCallToComplexColumnsOperationWithVariableValuesIfNeeded(
        builder,
        entityEnvironment,
        METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS,
        MANAGER_VARIABLE + ".getDbConnection()",
        OPERATION_HELPER_VARIABLE,
        OPERATION_BY_COLUMNS_VARIABLE);
  }

  private MethodSpec internalPersistIgnoringNull() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addParameter(BIND_VALUES_MAP, "values")
        .addParameter(entityDbManagerParameter())
        .addParameter(VARIABLE_ARGS_OPERATION_HELPER, OPERATION_HELPER_VARIABLE)
        .addParameter(operationByColumnsParameter())
        .returns(LONG);

    addPersistLoggingStatement(builder);
    addInternalPersistIgnoringNullMainBody(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        addCheckIdValidity(builder, FAILED_TO_PERSIST_ERR_MSG);
        addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
      }
    });

    return builder
        .addStatement("return id")
        .build();
  }

  private void addInternalPersistIgnoringNullMainBody(MethodSpec.Builder builder, Callback<MethodSpec.Builder> insertValidityCheck) {
    final String tableName = tableElement.getTableName();
    final ColumnElement idColumn = tableElement.getIdColumn();
    final boolean hasUniqueColumnsOtherThanId = tableElement.hasUniqueColumnsOtherThanId();
    final boolean idColumnNullable = idColumn.isNullable();

    addCallToComplexColumnsPersistIgnoringNullIfNeeded(builder);
    addBindToNotNullValues(builder);
    builder.addCode(entityEnvironment.getIdVariable())
        .addStatement("int rowsAffected = 0");
    if (hasUniqueColumnsOtherThanId) {
      builder.addCode(updateByColumnVariable(tableElement));
    } else if (idColumnNullable) {
      builder.beginControlFlow("if (id != null)");
    }
    builder.addStatement("rowsAffected = $L.compileStatement($L, $S, $L, values, $L, $L).executeUpdateDelete()",
        OPERATION_HELPER_VARIABLE,
        UPDATE.ordinal(),
        tableName,
        tableElement.getAllColumnsCount(),
        hasUniqueColumnsOtherThanId ? UPDATE_BY_COLUMN_VARIABLE : "\"" + idColumn.getColumnName() + "\"",
        MANAGER_VARIABLE);
    if (!hasUniqueColumnsOtherThanId && idColumnNullable) {
      builder.endControlFlow();
    }
    builder.beginControlFlow("if (rowsAffected <= 0)");
    addPersistUpdateFailedLoggingStatement(builder);
    if (idColumn.isAutoincrementId()) {
      builder.addStatement("values.remove($S)", idColumn.getColumnName());
    }
    builder.addStatement("id = $L.compileStatement($L, $S, $L, values, $S, $L).executeInsert()",
        OPERATION_HELPER_VARIABLE,
        INSERT.ordinal(),
        tableName,
        tableElement.getAllColumnsCount(),
        idColumn.getColumnName(),
        MANAGER_VARIABLE);
    addPersistAfterInsertLoggingStatement(builder);

    insertValidityCheck.call(builder);

    builder.endControlFlow();
  }

  private void addCallToComplexColumnsPersistIgnoringNullIfNeeded(MethodSpec.Builder builder) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    if (tableElement.hasAnyPersistedComplexColumns()) {
      final CodeBlock.Builder statementBuilder = CodeBlock.builder();
      if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
        statementBuilder.add("final long[] ids = ");
      }
      statementBuilder.add("$T.$L($L, $L, $L.getDbConnection(), $L, $L)",
          entityEnvironment.getDaoClassName(),
          METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS,
          ENTITY_VARIABLE,
          "values",
          MANAGER_VARIABLE,
          OPERATION_HELPER_VARIABLE,
          OPERATION_BY_COLUMNS_VARIABLE);
      statementBuilder.add(codeBlockEnd());
      builder.addCode(statementBuilder.build());
    }
  }

  private void addBindToNotNullValues(MethodSpec.Builder builder) {
    builder.addCode(statementWithImmutableIdsIfNeeded(
        tableElement,
        "$T.$L($L, values",
        daoClassName,
        METHOD_BIND_TO_NOT_NULL,
        ENTITY_VARIABLE));
  }

  private TypeSpec bulkPersist() {
    final TypeName interfaceType = ENTITY_BULK_PERSIST_BUILDER;
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_PERSIST, interfaceType, iterable, OBJECTS_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, interfaceType);
    addOperationByColumnToOperationBuilder(builder, interfaceType);
    return builder
        .addSuperinterface(interfaceType)
        .addField(TypeName.BOOLEAN, IGNORE_NULL_VALUES_VARIABLE, Modifier.PRIVATE)
        .addMethod(setIgnoreNullValues(interfaceType))
        .addMethod(bulkPersistExecute())
        .addMethod(bulkPersistObserve(builder))
        .build();
  }

  private MethodSpec bulkPersistObserve(TypeSpec.Builder typeBuilder) {
    final MethodSpec.Builder builder = operationRxCompletableMethod()
        .addAnnotation(Override.class);
    addRxCompletableCreateFromParentClass(builder);
    addRxCompletableFromEmitterToType(typeBuilder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addCode(entityDbVariablesForOperationBuilder(tableElement));
        addDisposableForEmitter(builder);

        builder.beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE);

        addBulkPersistObserveForNullableColumns(builder);

        builder.nextControlFlow("else");

        addBulkPersistObserveForFixedColumns(builder);

        builder.endControlFlow();
      }
    });
    return builder.build();
  }

  private void addBulkPersistObserveForNullableColumns(MethodSpec.Builder builder) {
    addBulkPersistTopBlockForNullableColumns(builder, false);
    addBulkPersistBottomBlockForObserve(builder);
  }

  private void addBulkPersistObserveForFixedColumns(MethodSpec.Builder builder) {
    addBulkPersistTopBlockForFixedColumns(builder, false);
    addBulkPersistBottomBlockForObserve(builder);
    builder.addStatement("$L.close()", OPERATION_HELPER_VARIABLE);
  }

  private void addBulkPersistBottomBlockForObserve(MethodSpec.Builder builder) {
    if (tableElement.hasAnyPersistedComplexColumns()) {
      addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers);
      builder.endControlFlow(); // .ignoreConflict
    } else {
      addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers,
          CodeBlock.builder()
              .addStatement("success = atLeastOneSuccess")
              .build());
    }
  }

  private MethodSpec bulkPersistExecute() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addCode(entityDbVariablesForOperationBuilder(tableElement));

    builder.beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE);

    addBulkPersistExecuteForNullableColumns(builder);

    builder.nextControlFlow("else");

    addBulkPersistExecuteForFixedColumns(builder);

    builder.endControlFlow();
    return builder.build();
  }

  private void addBulkPersistExecuteForNullableColumns(MethodSpec.Builder builder) {
    addBulkPersistTopBlockForNullableColumns(builder, true);

    if (tableElement.hasAnyPersistedComplexColumns()) {
      ModelPersistingGenerator.addTransactionEndBlock(builder,
          allTableTriggers,
          CodeBlock.builder()
              .addStatement("success = true")
              .build(),
          CodeBlock.builder()
              .addStatement("return true")
              .build(),
          "return false",
          false);
      builder.endControlFlow(); // .ignoreConflict
    } else {
      ModelPersistingGenerator.addTransactionEndBlock(builder,
          allTableTriggers,
          CodeBlock.builder()
              .addStatement("success = atLeastOneSuccess")
              .build(),
          CodeBlock.builder()
              .addStatement("return atLeastOneSuccess")
              .build(),
          "return false",
          false);
    }
  }

  private void addBulkPersistTopBlockForNullableColumns(MethodSpec.Builder builder,
                                                        final boolean forExecuteMethod) {
    builder
        .addCode(variableArgsOpHelperVariable())
        .addCode(bindValuesVariable(tableElement));

    if (tableElement.hasAnyPersistedComplexColumns()) {
      builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

      builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN);
      if (!forExecuteMethod) {
        builder.beginControlFlow("try");
      }
      addBulkPersistLoop(builder, new Callback<MethodSpec.Builder>() {
        @Override
        public void call(MethodSpec.Builder builder) {
          if (!forExecuteMethod) {
            addOperationFailedWhenDisposed(builder);
          }
          builder.addStatement("final $T $L = $L.newTransaction()",
              TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
              .beginControlFlow("try")
              .addStatement("$L($L, values, $L, $L, $L)",
                  METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES,
                  ENTITY_VARIABLE,
                  MANAGER_VARIABLE,
                  OPERATION_HELPER_VARIABLE,
                  OPERATION_BY_COLUMNS_VARIABLE)
              .addStatement("atLeastOneSuccess = true")
              .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
              .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION)
              .addStatement("continue")
              .nextControlFlow("finally")
              .addStatement("$L.end()", TRANSACTION_VARIABLE)
              .endControlFlow();
        }
      });

      if (forExecuteMethod) {
        builder.beginControlFlow("if (atLeastOneSuccess)");
        addTableTriggersSendingStatement(builder, allTableTriggers);
        builder.endControlFlow();
        builder.addStatement("return atLeastOneSuccess");
      } else {
        builder.nextControlFlow("catch ($T e)", Throwable.class)
            .addCode(emitterOnError())
            .nextControlFlow("finally");
        builder.beginControlFlow("if (atLeastOneSuccess)");
        addTableTriggersSendingStatement(builder, allTableTriggers);
        builder.endControlFlow()
            .addStatement(emitterOnComplete())
            .endControlFlow();
      }

      builder.nextControlFlow("else");
    } else {
      builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN);
    }

    addTransactionStartBlock(builder);
    addBulkPersistLoop(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        if (!forExecuteMethod) {
          addOperationFailedWhenDisposed(builder);
        }
        if (tableElement.hasAnyPersistedComplexColumns()) {
          builder.addStatement("$L($L, values, $L, $L, $L)",
              METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES,
              ENTITY_VARIABLE,
              MANAGER_VARIABLE,
              OPERATION_HELPER_VARIABLE,
              OPERATION_BY_COLUMNS_VARIABLE);
        } else {
          addInternalPersistIgnoringNullMainBody(builder, new Callback<MethodSpec.Builder>() {
            @Override
            public void call(MethodSpec.Builder builder) {
              addNormalLoopBodyValidityCheck(builder);
            }
          });
        }
      }
    });
  }

  private void addBulkPersistExecuteForFixedColumns(MethodSpec.Builder builder) {
    addBulkPersistTopBlockForFixedColumns(builder, true);

    if (tableElement.hasAnyPersistedComplexColumns()) {
      ModelPersistingGenerator.addTransactionEndBlock(builder,
          allTableTriggers,
          "return true",
          "return false",
          true);
      builder.endControlFlow(); // .ignoreConflict
    } else {
      ModelPersistingGenerator.addTransactionEndBlock(builder,
          allTableTriggers,
          CodeBlock.builder()
              .addStatement("success = atLeastOneSuccess")
              .build(),
          CodeBlock.builder()
              .addStatement("return atLeastOneSuccess")
              .build(),
          "return false",
          true);
    }
  }

  private void addBulkPersistTopBlockForFixedColumns(MethodSpec.Builder builder,
                                                     final boolean forExecuteMethod) {
    builder.addCode(opByColumnHelperVariable(PERSIST))
        .addCode(updateStatementVariableFromOpHelper(tableElement, UPDATE_STATEMENT_VARIABLE))
        .addCode(insertStatementVariableFromOpHelper(tableElement, INSERT_STATEMENT_VARIABLE));
    if (tableElement.hasUniqueColumnsOtherThanId()) {
      builder.addCode(updateByColumnVariable(tableElement));
    }
    if (tableElement.hasAnyPersistedComplexColumns()) {
      builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

      builder
          .addStatement("$T atLeastOneSuccess = false", BOOLEAN)
          .beginControlFlow("try");
      addBulkPersistLoopWithCompiledStatements(builder, new Callback<MethodSpec.Builder>() {
        @Override
        public void call(MethodSpec.Builder builder) {
          if (!forExecuteMethod) {
            addOperationFailedWhenDisposed(builder);
          }
          addBulkPersistComplexColumnIgnoreConflictLoopBody(builder);
        }
      });
      if (forExecuteMethod) {
        builder.beginControlFlow("if (atLeastOneSuccess)");
        addTableTriggersSendingStatement(builder, allTableTriggers);
        builder.endControlFlow()
            .addStatement("return atLeastOneSuccess");
      } else {
        builder.nextControlFlow("catch ($T e)", Throwable.class)
            .addCode(emitterOnError());
      }
      builder.nextControlFlow("finally");
      if (!forExecuteMethod) {
        builder.beginControlFlow("if (atLeastOneSuccess)");
        addTableTriggersSendingStatement(builder, allTableTriggers);
        builder.endControlFlow()
            .addStatement(emitterOnComplete());
      }
      builder.addStatement("$L.close()", OPERATION_HELPER_VARIABLE)
          .endControlFlow();

      builder.nextControlFlow("else");
    } else {
      builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN);
    }

    addTransactionStartBlock(builder);
    addBulkPersistLoopWithCompiledStatements(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        if (!forExecuteMethod) {
          addOperationFailedWhenDisposed(builder);
        }
        addBulkPersistNormalLoopBody(builder);
      }
    });
  }

  private void addBulkPersistNormalLoopBody(MethodSpec.Builder builder) {
    addBulkPersistExecuteMainBody(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        addNormalLoopBodyValidityCheck(builder);
      }
    });
  }

  private void addNormalLoopBodyValidityCheck(MethodSpec.Builder builder) {
    if (tableElement.hasAnyPersistedComplexColumns()) {
      addCheckIdValidity(builder, FAILED_TO_PERSIST_ERR_MSG);
      addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
    } else {
      addIdValidityRespectingConflictAbort(builder, tableElement, daoClassName, FAILED_TO_PERSIST_ERR_MSG);
      builder
          .nextControlFlow("else")
          .addStatement("atLeastOneSuccess = true");
    }
  }

  private void addBulkPersistComplexColumnIgnoreConflictLoopBody(MethodSpec.Builder builder) {
    builder.addStatement("final $T $L = $L.newTransaction()",
        TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
        .beginControlFlow("try");

    addBulkPersistExecuteMainBody(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.beginControlFlow("if (id != -1)");
        addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
        builder.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
            .addStatement("atLeastOneSuccess = true")
            .endControlFlow() // if (id != -1)
            .nextControlFlow("else")
            .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
            .addStatement("atLeastOneSuccess = true");
      }
    });

    builder.nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION)
        .addStatement("continue")
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .endControlFlow();
  }

  private void addBulkPersistExecuteMainBody(MethodSpec.Builder builder, Callback<MethodSpec.Builder> insertValidityCheck) {
    final boolean idColumnNullable = tableElement.getIdColumn().isNullable();
    addCallToComplexColumnsPersistIfNeeded(builder);
    if (idColumnNullable) {
      builder.addStatement("int rowsAffected = 0")
          .addCode(entityEnvironment.getIdVariable())
          .beginControlFlow("if (id != null)");
    }
    addBindToUpdateStatement(builder, UPDATE_STATEMENT_VARIABLE, "id", tableElement.hasUniqueColumnsOtherThanId());
    if (idColumnNullable) {
      builder.addStatement("rowsAffected = $L.executeUpdateDelete()", UPDATE_STATEMENT_VARIABLE)
          .endControlFlow()
          .beginControlFlow("if (rowsAffected <= 0)");
    } else {
      builder.beginControlFlow("if ($L.executeUpdateDelete() <= 0)", UPDATE_STATEMENT_VARIABLE);
    }
    addPersistUpdateFailedLoggingStatement(builder);
    addBindToInsertStatement(builder, tableElement, daoClassName, INSERT_STATEMENT_VARIABLE);
    if (idColumnNullable) {
      builder.addStatement("id = $L.executeInsert()", INSERT_STATEMENT_VARIABLE);
    } else {
      builder.addStatement("final $T id = $L.executeInsert()", LONG, INSERT_STATEMENT_VARIABLE);
    }
    addAfterInsertLoggingStatement(builder);

    insertValidityCheck.call(builder);

    builder.endControlFlow();
  }

  private void addBulkPersistLoopWithCompiledStatements(MethodSpec.Builder builder, Callback<MethodSpec.Builder> body) {
    builder
        .beginControlFlow("synchronized ($L)", UPDATE_STATEMENT_VARIABLE)
        .beginControlFlow("synchronized ($L)", INSERT_STATEMENT_VARIABLE)
        .beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addPersistLoggingStatement(builder);
    body.call(builder);
    builder
        .endControlFlow()
        .endControlFlow()
        .endControlFlow();
  }

  private void addBulkPersistLoop(MethodSpec.Builder builder, Callback<MethodSpec.Builder> body) {
    builder.beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addPersistLoggingStatement(builder);
    body.call(builder);
    builder.endControlFlow();
  }

  private void addPersistLoggingStatement(MethodSpec.Builder builder) {
    if (BaseProcessor.GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"PERSIST\\n  table: $L\\n  object: %s\", $L.toString())",
          SQLITE_MAGIC, LOG_UTIL, tableElement.getTableName(), ENTITY_VARIABLE);
    }
  }

  private void addPersistUpdateFailedLoggingStatement(MethodSpec.Builder builder) {
    if (BaseProcessor.GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"PERSIST update failed; trying insertion\")", SQLITE_MAGIC, LOG_UTIL);
    }
  }

  private void addPersistAfterInsertLoggingStatement(MethodSpec.Builder builder) {
    if (BaseProcessor.GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"PERSIST insert id: %s\", id)", SQLITE_MAGIC, LOG_UTIL);
    }
  }
}
