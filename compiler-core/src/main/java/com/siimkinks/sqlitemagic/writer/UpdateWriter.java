package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.ReturnCallback2;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.BaseProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_UPDATE_ERR_MSG;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_UPDATE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_UPDATE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.SUPPORT_SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.TRANSACTION;
import static com.siimkinks.sqlitemagic.WriterUtil.addConflictAlgorithmToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addOperationByColumnToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxActionToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableCreateFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableFromEmitterToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnComplete;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnError;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbVariablesForOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.opByColumnHelperVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationByColumnsParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.operationHelperParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxCompletableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.WriterUtil.updateStatementVariableFromOpHelper;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_UNIQUE_COLUMN;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INTERNAL_UPDATE;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnFromProvidedIdsBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnToStatementBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addDisposableForEmitter;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addIdNullCheck;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addImmutableIdsParameterIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addOperationFailedWhenDisposed;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxCompletableEmitterTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addThrowOperationFailedExceptionWithEntityVariable;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.updateByColumnVariable;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_BY_COLUMNS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_HELPER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.STATEMENT_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.Operation.UPDATE;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateWriter implements OperationWriter {

  private final EntityEnvironment entityEnvironment;
  private final TableElement tableElement;
  private final TypeName tableElementTypeName;
  private final Set<TableElement> allTableTriggers;

  private final TypeName iterable;
  private final ClassName daoClassName;

  public static UpdateWriter create(EntityEnvironment entityEnvironment, Set<TableElement> allTableTriggers) {
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
    classBuilder.addMethod(bindToUpdateStatement());
    if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
      classBuilder.addMethod(bindToUpdateStatementWithComplexColumns());
    }

    addUpdateMethodInternalCallOnComplexColumnsIfNeeded(classBuilder);
  }

  @Override
  public void writeHandler(TypeSpec.Builder classBuilder) {
    final MethodSpec internalUpdate = internalUpdate();
    classBuilder
        .addMethod(internalUpdate)
        .addType(update(internalUpdate))
        .addType(bulkUpdate());
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private MethodSpec bindToUpdateStatement() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_UPDATE_STATEMENT)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(SUPPORT_SQLITE_STATEMENT, "statement")
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addStatement("statement.clearBindings()");
    int colPos = 1;
    for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
      addBindColumnToStatementBlock(builder, colPos, columnElement);
      colPos++;
    }
    return builder.build();
  }

  private MethodSpec bindToUpdateStatementWithComplexColumns() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(SUPPORT_SQLITE_STATEMENT, "statement")
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addStatement("statement.clearBindings()");
    addImmutableIdsParameterIfNeeded(builder, tableElement);
    int colPos = 1;
    int immutableIdColPos = 0;
    for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
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

  private void addUpdateMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder) {
    addUpdateMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment,
        METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
        METHOD_INTERNAL_UPDATE,
        COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
        connectionImplParameter(),
        operationHelperParameter(),
        operationByColumnsParameter());
  }

  private void addUpdateMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder,
                                                                   EntityEnvironment entityEnvironment,
                                                                   String methodName,
                                                                   String callableMethod,
                                                                   ReturnCallback2<String, ParameterSpec, ColumnElement> paramEval,
                                                                   ParameterSpec... params) {
    TableElement tableElement = entityEnvironment.getTableElement();
    if (tableElement.hasAnyPersistedComplexColumns()) {
      MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
          .addModifiers(STATIC_METHOD_MODIFIERS)
          .addParameter(entityEnvironment.getTableElementTypeName(), ENTITY_VARIABLE)
          .returns(TypeName.BOOLEAN);
      for (ParameterSpec param : params) {
        methodBuilder.addParameter(param);
      }
      boolean first = true;
      methodBuilder.addCode("return ");
      CodeBlock.Builder codeBuilder = CodeBlock.builder();
      final List<ColumnElement> allColumns = tableElement.getAllColumns();
      boolean indent = false;
      for (ColumnElement columnElement : allColumns) {
        if (columnElement.isHandledRecursively()) {
          final String valueGetter = columnElement.valueGetter(ENTITY_VARIABLE);
          final ClassName referencedModelHandler = EntityEnvironment.getGeneratedHandlerClassName(columnElement.getReferencedTable());
          if (!first) {
            codeBuilder.add("\n");
            if (!indent) {
              indent = true;
              codeBuilder.indent();
            }
            codeBuilder.add("&& ");
          }
          if (columnElement.isNullable()) {
            codeBuilder.add("($L == null || ", valueGetter);
          }
          final StringBuilder sb = new StringBuilder();
          for (ParameterSpec param : params) {
            sb.append(", ")
                .append(paramEval.call(param, columnElement));
          }
          codeBuilder.add("$T.$L($L$L)",
              referencedModelHandler,
              callableMethod,
              valueGetter,
              sb.toString());
          if (columnElement.isNullable()) {
            codeBuilder.add(")");
          }
          if (first) {
            first = false;
          }
        }
      }
      codeBuilder.add(";");
      if (indent) {
        codeBuilder.unindent();
      }
      codeBuilder.add("\n");
      methodBuilder.addCode(codeBuilder.build());
      daoClassBuilder.addMethod(methodBuilder.build());
    }
  }

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private TypeSpec update(MethodSpec update) {
    final MethodSpec updateExecute = updateExecute(update);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_UPDATE, ENTITY_UPDATE_BUILDER, tableElementTypeName, ENTITY_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, ENTITY_UPDATE_BUILDER);
    addOperationByColumnToOperationBuilder(builder, ENTITY_UPDATE_BUILDER);
    return builder.addSuperinterface(ENTITY_UPDATE_BUILDER)
        .addMethod(updateExecute)
        .addMethod(updateObserve(builder, updateExecute))
        .build();
  }

  private MethodSpec updateExecute(MethodSpec update) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addCode(entityDbVariablesForOperationBuilder(tableElement))
        .addCode(opByColumnHelperVariable(UPDATE));
    final boolean hasComplexColumns = tableElement.hasAnyPersistedComplexColumns();
    if (hasComplexColumns) {
      addTransactionStartBlock(builder);
    } else {
      builder.beginControlFlow("try");
    }

    final FormatData internalMethodCall = FormatData.create("$N($L, $L, $L, $L)",
        update,
        ENTITY_VARIABLE,
        MANAGER_VARIABLE,
        OPERATION_HELPER_VARIABLE,
        OPERATION_BY_COLUMNS_VARIABLE);
    if (hasComplexColumns) {
      addCallToInternalUpdateWithTransactionHandling(builder, internalMethodCall);
    } else {
      addCallToInternalUpdate(builder, allTableTriggers, internalMethodCall);
    }

    if (hasComplexColumns) {
      addUpdateTransactionEndBlock(builder, allTableTriggers);
    } else {
      builder.nextControlFlow("finally")
          .addStatement("$L.close()", OPERATION_HELPER_VARIABLE)
          .endControlFlow();
    }

    return builder.build();
  }

  private MethodSpec updateObserve(TypeSpec.Builder typeBuilder, final MethodSpec updateExecute) {
    final MethodSpec.Builder builder = operationRxCompletableMethod()
        .addAnnotation(Override.class);
    addRxActionToType(typeBuilder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.beginControlFlow("if (!$N() && conflictAlgorithm != $T.CONFLICT_IGNORE)", updateExecute, SQLITE_DATABASE)
            .addStatement("throw new $T($S + $L)",
                OPERATION_FAILED_EXCEPTION, FAILED_TO_UPDATE_ERR_MSG, ENTITY_VARIABLE)
            .endControlFlow();
      }
    });
    addRxCompletableFromParentClass(builder);
    return builder.build();
  }

  private MethodSpec internalUpdate() {
    final boolean hasAnyPersistedComplexColumns = tableElement.hasAnyPersistedComplexColumns();
    final boolean hasUniqueColumnsOtherThanId = tableElement.hasUniqueColumnsOtherThanId();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INTERNAL_UPDATE)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityParameter(tableElementTypeName))
        .addParameter(entityDbManagerParameter())
        .addParameter(operationHelperParameter())
        .addParameter(operationByColumnsParameter())
        .returns(TypeName.BOOLEAN);
    addUpdateLoggingStatement(builder);

    if (hasAnyPersistedComplexColumns && GENERATE_LOGGING) {
      builder.addStatement("final int rowsAffected");
    }
    if (hasUniqueColumnsOtherThanId) {
      builder.addCode(updateByColumnVariable(tableElement));
    } else {
      addIdColumnNullCheckIfNeeded(builder);
    }

    builder.addCode(updateStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE))
        .beginControlFlow("synchronized ($L)", STATEMENT_VARIABLE);
    addBindToUpdateStatement(builder);

    if (!hasAnyPersistedComplexColumns) {
      if (!GENERATE_LOGGING) {
        builder.addStatement("return $L.executeUpdateDelete() > 0", STATEMENT_VARIABLE);
      } else {
        builder.addStatement("final int rowsAffected = $L.executeUpdateDelete()", STATEMENT_VARIABLE);
        addAfterUpdateLoggingStatement(builder);
        builder.addStatement("return rowsAffected > 0");
      }
    } else {
      if (!GENERATE_LOGGING) {
        builder.beginControlFlow("if ($L.executeUpdateDelete() > 0)", STATEMENT_VARIABLE)
            .addStatement("return $T.$L($L, $L.getDbConnection(), $L, $L)",
                daoClassName,
                METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
                ENTITY_VARIABLE,
                MANAGER_VARIABLE,
                OPERATION_HELPER_VARIABLE,
                OPERATION_BY_COLUMNS_VARIABLE)
            .endControlFlow()
            .addStatement("return false");
      } else {
        builder.addStatement("rowsAffected = $L.executeUpdateDelete()", STATEMENT_VARIABLE);
        addAfterUpdateLoggingStatement(builder);
        builder.endControlFlow()
            .beginControlFlow("if (rowsAffected > 0)")
            .addStatement("return $T.$L($L, $L.getDbConnection(), $L, $L)",
                daoClassName,
                METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
                ENTITY_VARIABLE,
                MANAGER_VARIABLE,
                OPERATION_HELPER_VARIABLE,
                OPERATION_BY_COLUMNS_VARIABLE)
            .endControlFlow()
            .addStatement("return false");
        return builder.build();
      }
    }
    builder.endControlFlow();
    return builder.build();
  }

  private void addIdColumnNullCheckIfNeeded(MethodSpec.Builder builder) {
    if (isIdColumnNullable()) {
      builder.addCode(entityEnvironment.getFinalIdVariable());
      addIdNullCheck(builder, "Can't execute update - id column null");
    }
  }

  private boolean isIdColumnNullable() {
    return tableElement.getIdColumn().isNullable();
  }

  private void addCallToInternalUpdateWithTransactionHandling(MethodSpec.Builder builder,
                                                              FormatData internalMethodCall) {
    builder.beginControlFlow(String.format("if (%s)", internalMethodCall.getFormat()), internalMethodCall.getArgs())
        .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("success = true")
        .endControlFlow()
        .addStatement("return success");
  }

  private void addCallToInternalUpdate(MethodSpec.Builder builder, Set<TableElement> allTableTriggers, FormatData internalMethodCall) {
    builder.beginControlFlow(String.format("if (%s)", internalMethodCall.getFormat()), internalMethodCall.getArgs());
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.addStatement("return true")
        .endControlFlow()
        .addStatement("return false");
  }

  private void addUpdateTransactionEndBlock(MethodSpec.Builder builder,
                                            Set<TableElement> allTableTriggers) {
    builder.nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .beginControlFlow("if (success)");
    addTableTriggersSendingStatement(builder, allTableTriggers);
    builder.endControlFlow()
        .addStatement("$L.close()", OPERATION_HELPER_VARIABLE)
        .endControlFlow();
  }

  private TypeSpec bulkUpdate() {
    final TypeName interfaceType = ENTITY_BULK_UPDATE_BUILDER;
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_UPDATE, interfaceType, iterable, OBJECTS_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, interfaceType);
    addOperationByColumnToOperationBuilder(builder, interfaceType);
    return builder
        .addSuperinterface(interfaceType)
        .addMethod(bulkUpdateExecute())
        .addMethod(bulkUpdateObserve(builder))
        .build();
  }

  private MethodSpec bulkUpdateObserve(TypeSpec.Builder typeBuilder) {
    final MethodSpec.Builder builder = operationRxCompletableMethod()
        .addAnnotation(Override.class);
    addRxCompletableCreateFromParentClass(builder);
    addRxCompletableFromEmitterToType(typeBuilder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        addDisposableForEmitter(builder);
        builder.addCode(entityDbVariablesForOperationBuilder(tableElement))
            .addCode(opByColumnHelperVariable(UPDATE))
            .addCode(updateStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE));
        if (tableElement.hasUniqueColumnsOtherThanId()) {
          builder.addCode(updateByColumnVariable(tableElement));
        }

        if (tableElement.hasAnyPersistedComplexColumns()) {
          builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

          builder.addStatement("$T atLeastOneSuccess = false", TypeName.BOOLEAN)
              .beginControlFlow("try");

          addBulkUpdateLoop(builder, new Callback<MethodSpec.Builder>() {
            @Override
            public void call(MethodSpec.Builder builder) {
              addOperationFailedWhenDisposed(builder);
              addBulkUpdateComplexColumnIgnoreConflictLoopBody(builder);
            }
          });
          builder.nextControlFlow("catch ($T e)", Throwable.class)
              .addCode(emitterOnError())
              .nextControlFlow("finally")
              .beginControlFlow("if (atLeastOneSuccess)");
          addTableTriggersSendingStatement(builder, allTableTriggers);
          builder.endControlFlow()
              .addStatement(emitterOnComplete())
              .endControlFlow();

          builder.nextControlFlow("else");
        } else {
          builder.addStatement("$T atLeastOneSuccess = false", TypeName.BOOLEAN);
        }

        addTransactionStartBlock(builder);
        addBulkUpdateLoop(builder, new Callback<MethodSpec.Builder>() {
          @Override
          public void call(MethodSpec.Builder builder) {
            addOperationFailedWhenDisposed(builder);
            addBulkUpdateNormalLoopBody(builder);
          }
        });
        if (tableElement.hasAnyPersistedComplexColumns()) {
          addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers);
          builder.endControlFlow();
        } else {
          addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers,
              CodeBlock.builder()
                  .addStatement("success = atLeastOneSuccess")
                  .build());
        }
        builder.addStatement("$L.close()", OPERATION_HELPER_VARIABLE);
      }
    });
    return builder.build();
  }

  private MethodSpec bulkUpdateExecute() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addCode(entityDbVariablesForOperationBuilder(tableElement))
        .addCode(opByColumnHelperVariable(UPDATE))
        .addCode(updateStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE));

    if (tableElement.hasUniqueColumnsOtherThanId()) {
      builder.addCode(updateByColumnVariable(tableElement));
    }

    if (tableElement.hasAnyPersistedComplexColumns()) {
      builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

      builder.beginControlFlow("try")
          .addStatement("$T atLeastOneSuccess = false", TypeName.BOOLEAN);
      addBulkUpdateLoop(builder, new Callback<MethodSpec.Builder>() {
        @Override
        public void call(MethodSpec.Builder builder) {
          addBulkUpdateComplexColumnIgnoreConflictLoopBody(builder);
        }
      });
      builder.beginControlFlow("if (atLeastOneSuccess)");
      addTableTriggersSendingStatement(builder, allTableTriggers);
      builder.endControlFlow()
          .addStatement("return atLeastOneSuccess")
          .nextControlFlow("finally")
          .addStatement("$L.close()", OPERATION_HELPER_VARIABLE)
          .endControlFlow();

      builder.nextControlFlow("else");
    } else {
      builder.addStatement("$T atLeastOneSuccess = false", TypeName.BOOLEAN);
    }

    addTransactionStartBlock(builder);
    addBulkUpdateLoop(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        addBulkUpdateNormalLoopBody(builder);
      }
    });

    if (tableElement.hasAnyPersistedComplexColumns()) {
      ModelPersistingGenerator.addTransactionEndBlock(builder,
          allTableTriggers,
          "return true",
          "return false",
          true);
      builder.endControlFlow();
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
    return builder.build();
  }

  private void addBulkUpdateNormalLoopBody(MethodSpec.Builder builder) {
    addNullableIdCheckIfNeeded(builder);
    addBindToUpdateStatement(builder);
    if (tableElement.hasAnyPersistedComplexColumns()) {
      builder.beginControlFlow("if ($L.executeUpdateDelete() <= 0 || !$T.$L($L, $L.getDbConnection(), $L, $L))",
          STATEMENT_VARIABLE,
          daoClassName,
          METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
          ENTITY_VARIABLE,
          MANAGER_VARIABLE,
          OPERATION_HELPER_VARIABLE,
          OPERATION_BY_COLUMNS_VARIABLE);
    } else {
      builder.beginControlFlow("if ($L.executeUpdateDelete() <= 0)", STATEMENT_VARIABLE)
          .beginControlFlow("if (!$L.ignoreConflict)", OPERATION_HELPER_VARIABLE);
    }
    addThrowOperationFailedExceptionWithEntityVariable(builder, FAILED_TO_UPDATE_ERR_MSG);
    builder.endControlFlow();
    if (!tableElement.hasAnyPersistedComplexColumns()) {
      builder.nextControlFlow("else")
          .addStatement("atLeastOneSuccess = true")
          .endControlFlow();
    }
  }

  private void addBulkUpdateComplexColumnIgnoreConflictLoopBody(MethodSpec.Builder builder) {
    builder.addStatement("final $T $L = $L.newTransaction()",
        TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
        .beginControlFlow("try");
    addNullableIdCheckIfNeeded(builder);
    addBindToUpdateStatement(builder);
    builder.beginControlFlow("if ($L.executeUpdateDelete() > 0 && $T.$L($L, $L.getDbConnection(), $L, $L))",
        STATEMENT_VARIABLE,
        daoClassName,
        METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
        ENTITY_VARIABLE,
        MANAGER_VARIABLE,
        OPERATION_HELPER_VARIABLE,
        OPERATION_BY_COLUMNS_VARIABLE)
        .addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("atLeastOneSuccess = true")
        .endControlFlow()
        .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION)
        .addStatement("continue")
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .endControlFlow();
  }

  private void addBindToUpdateStatement(MethodSpec.Builder builder) {
    builder.addStatement("$T.$L($L, $L)",
        daoClassName,
        METHOD_BIND_TO_UPDATE_STATEMENT,
        STATEMENT_VARIABLE,
        ENTITY_VARIABLE);
    if (tableElement.hasUniqueColumnsOtherThanId()) {
      builder.addStatement("$T.$L($L, $L, updateByColumn, $L)",
          daoClassName,
          METHOD_BIND_UNIQUE_COLUMN,
          STATEMENT_VARIABLE,
          tableElement.getAllColumnsCount(),
          ENTITY_VARIABLE);
    } else {
      final CodeBlock.Builder bindIdBuilder = CodeBlock.builder()
          .add("$L.bindLong($L, ",
              STATEMENT_VARIABLE,
              tableElement.getAllColumnsCount());
      if (!tableElement.getIdColumn().isNullable()) {
        entityEnvironment.addInlineIdVariable(bindIdBuilder);
      } else {
        bindIdBuilder.add("id");
      }
      bindIdBuilder.add(");\n");
      builder.addCode(bindIdBuilder.build());
    }
  }

  private void addNullableIdCheckIfNeeded(MethodSpec.Builder builder) {
    if (isIdColumnNullable() && !tableElement.hasUniqueColumnsOtherThanId()) {
      builder.addCode(entityEnvironment.getFinalIdVariable());
      addIdNullCheck(builder, "Can't execute update - id column null");
    }
  }

  private void addBulkUpdateLoop(MethodSpec.Builder builder, Callback<MethodSpec.Builder> body) {
    builder.beginControlFlow("synchronized ($L)", STATEMENT_VARIABLE)
        .beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addUpdateLoggingStatement(builder);
    body.call(builder);
    builder.endControlFlow()
        .endControlFlow();
  }

  private void addUpdateLoggingStatement(MethodSpec.Builder builder) {
    if (GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"UPDATE\\n  table: $L\\n  object: %s\", $L.toString())",
          SQLITE_MAGIC, LOG_UTIL, tableElement.getTableName(), ENTITY_VARIABLE);
    }
  }

  private void addAfterUpdateLoggingStatement(MethodSpec.Builder builder) {
    if (GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"UPDATE rows affected: %s\", rowsAffected)", SQLITE_MAGIC, LOG_UTIL);
    }
  }
}
