package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.ReturnCallback;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;

import static com.siimkinks.sqlitemagic.BaseProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_INSERT_ERR_MSG;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_INSERT_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_INSERT_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.SUPPORT_SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.TRANSACTION;
import static com.siimkinks.sqlitemagic.WriterUtil.addCallableToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addConflictAlgorithmToOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableCreateFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableFromEmitterToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromCallableParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnComplete;
import static com.siimkinks.sqlitemagic.WriterUtil.emitterOnError;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbVariablesForOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.insertStatementVariableFromOpHelper;
import static com.siimkinks.sqlitemagic.WriterUtil.opHelperVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationHelperParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxCompletableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_INSERT_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INTERNAL_INSERT;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnFromProvidedIdsBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnToStatementBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addDisposableForEmitter;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addIdValidityRespectingConflictAbort;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addImmutableIdsParameterIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addInlineExecuteInsertWithCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addMethodInternalCallOnComplexColumnsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addOperationFailedWhenDisposed;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxCompletableEmitterTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addSetIdStatementIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.isIdSettingNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.statementWithImmutableIdsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_HELPER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.STATEMENT_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.Operation.INSERT;
import static com.squareup.javapoet.TypeName.BOOLEAN;
import static com.squareup.javapoet.TypeName.LONG;

// TODO optimization for simple model single item operations - no need to allocate opHelper - call method directly
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InsertWriter implements OperationWriter {

  private final EntityEnvironment entityEnvironment;
  private final TableElement tableElement;
  private final TypeName tableElementTypeName;
  private final Set<TableElement> allTableTriggers;

  private final TypeName iterable;
  private final ClassName daoClassName;

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
  }

  public void writeHandler(TypeSpec.Builder classBuilder) {
    final MethodSpec internalInsert = internalInsert();
    classBuilder
        .addMethod(internalInsert)
        .addType(insert(internalInsert))
        .addType(bulkInsert());
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private MethodSpec bindToInsertStatement() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_INSERT_STATEMENT)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(SUPPORT_SQLITE_STATEMENT, "statement")
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
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment,
        METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS,
        COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
        new ReturnCallback<String, ColumnElement>() {
          @Override
          public String call(ColumnElement columnElement) {
            return METHOD_INTERNAL_INSERT;
          }
        },
        connectionImplParameter(),
        operationHelperParameter());
  }

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private MethodSpec internalInsert() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_INTERNAL_INSERT)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityParameter(tableElementTypeName))
        .addParameter(entityDbManagerParameter())
        .addParameter(operationHelperParameter())
        .returns(LONG);
    addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
    addInsertLoggingStatement(builder, tableElement);
    builder.addStatement("final long id")
        .addCode(insertStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE))
        .beginControlFlow("synchronized ($L)", STATEMENT_VARIABLE);
    addBindToInsertStatement(builder, tableElement, daoClassName, STATEMENT_VARIABLE);
    builder.addStatement("id = $L.executeInsert()", STATEMENT_VARIABLE)
        .endControlFlow();
    addAfterInsertLoggingStatement(builder);
    addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
    return builder
        .addStatement("return id")
        .build();
  }

  static void addBindToInsertStatement(MethodSpec.Builder builder, TableElement tableElement, ClassName daoClassName, String insertStmVariableName) {
    builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "$T.$L($L, $L",
        daoClassName,
        METHOD_BIND_TO_INSERT_STATEMENT,
        insertStmVariableName,
        ENTITY_VARIABLE));
  }

  private TypeSpec insert(MethodSpec insert) {
    final TypeName interfaceType = ENTITY_INSERT_BUILDER;
    final MethodSpec insertExecute = insertExecute(insert);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_INSERT, interfaceType, tableElementTypeName, ENTITY_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, interfaceType);
    return builder.addSuperinterface(interfaceType)
        .addMethod(insertExecute)
        .addMethod(insertObserve(builder, insertExecute))
        .build();
  }

  private MethodSpec insertExecute(MethodSpec insert) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(LONG)
        .addCode(entityDbVariablesForOperationBuilder(tableElement))
        .addCode(opHelperVariable(INSERT));

    final boolean hasAnyPersistedComplexColumns = tableElement.hasAnyPersistedComplexColumns();
    addTopMethodStartBlock(builder, hasAnyPersistedComplexColumns);

    builder.addStatement("final $T id = $N($L, $L, $L)",
        LONG, insert, ENTITY_VARIABLE, MANAGER_VARIABLE, OPERATION_HELPER_VARIABLE);

    final String returnStatement = "return id";
    final String failReturnStatement = "return -1";
    ModelPersistingGenerator.addTopMethodEndBlock(builder,
        allTableTriggers,
        hasAnyPersistedComplexColumns,
        returnStatement,
        failReturnStatement,
        true);

    return builder.build();
  }

  private MethodSpec insertObserve(TypeSpec.Builder typeBuilder, final MethodSpec insertExecute) {
    final TypeName entityTypeName = LONG.box();
    final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
        .addAnnotation(Override.class);
    addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addStatement("final $T id = $N()", LONG, insertExecute)
            .beginControlFlow("if (id == -1 && conflictAlgorithm != $T.CONFLICT_IGNORE)", SQLITE_DATABASE)
            .addStatement("throw new $T($S + $L)",
                OPERATION_FAILED_EXCEPTION, FAILED_TO_INSERT_ERR_MSG, ENTITY_VARIABLE)
            .endControlFlow()
            .addStatement("return id");
      }
    });
    addRxSingleCreateFromCallableParentClass(builder);
    return builder.build();
  }

  private TypeSpec bulkInsert() {
    final TypeName interfaceType = ENTITY_BULK_INSERT_BUILDER;
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_INSERT, interfaceType, iterable, OBJECTS_VARIABLE);
    addConflictAlgorithmToOperationBuilder(builder, interfaceType);
    return builder
        .addSuperinterface(interfaceType)
        .addMethod(bulkInsertExecute())
        .addMethod(bulkInsertObserve(builder))
        .build();
  }

  private MethodSpec bulkInsertExecute() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addCode(entityDbVariablesForOperationBuilder(tableElement))
        .addCode(opHelperVariable(INSERT))
        .addCode(insertStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE));

    if (tableElement.hasAnyPersistedComplexColumns()) {
      builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

      builder.beginControlFlow("try")
          .addStatement("$T atLeastOneSuccess = false", BOOLEAN);
      addBulkInsertLoop(builder, new Callback<MethodSpec.Builder>() {
        @Override
        public void call(MethodSpec.Builder builder) {
          addBulkInsertComplexColumnIgnoreConflictLoopBody(builder);
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
      builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN);
    }

    addTransactionStartBlock(builder);
    addBulkInsertLoop(builder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        addBulkInsertNormalLoopBody(builder);
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

  private MethodSpec bulkInsertObserve(TypeSpec.Builder typeBuilder) {
    final MethodSpec.Builder builder = operationRxCompletableMethod()
        .addAnnotation(Override.class);
    addRxCompletableCreateFromParentClass(builder);
    addRxCompletableFromEmitterToType(typeBuilder, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addCode(entityDbVariablesForOperationBuilder(tableElement))
            .addCode(opHelperVariable(INSERT));
        addDisposableForEmitter(builder);
        builder.addCode(insertStatementVariableFromOpHelper(tableElement, STATEMENT_VARIABLE));

        if (tableElement.hasAnyPersistedComplexColumns()) {
          builder.beginControlFlow("if ($L.ignoreConflict)", OPERATION_HELPER_VARIABLE);

          builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN)
              .beginControlFlow("try");
          addBulkInsertLoop(builder, new Callback<MethodSpec.Builder>() {
            @Override
            public void call(MethodSpec.Builder builder) {
              addOperationFailedWhenDisposed(builder);
              addBulkInsertComplexColumnIgnoreConflictLoopBody(builder);
            }
          });
          builder.nextControlFlow("catch ($T e)", Throwable.class)
              .addCode(emitterOnError())
              .nextControlFlow("finally")
              .beginControlFlow("if (atLeastOneSuccess)")
              .addStatement(emitterOnComplete());
          addTableTriggersSendingStatement(builder, allTableTriggers);
          builder.endControlFlow()
              .addStatement(emitterOnComplete())
              .endControlFlow();

          builder.nextControlFlow("else");
        } else {
          builder.addStatement("$T atLeastOneSuccess = false", BOOLEAN);
        }

        addTransactionStartBlock(builder);
        addBulkInsertLoop(builder, new Callback<MethodSpec.Builder>() {
          @Override
          public void call(MethodSpec.Builder builder) {
            addOperationFailedWhenDisposed(builder);
            addBulkInsertNormalLoopBody(builder);
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

  private void addBulkInsertLoop(MethodSpec.Builder builder, Callback<MethodSpec.Builder> body) {
    builder.beginControlFlow("synchronized ($L)", STATEMENT_VARIABLE)
        .beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addInsertLoggingStatement(builder, tableElement);
    body.call(builder);
    builder.endControlFlow()
        .endControlFlow();
  }

  private void addBulkInsertNormalLoopBody(MethodSpec.Builder builder) {
    if (tableElement.hasAnyPersistedComplexColumns()) {
      addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
      addBindToInsertStatement(builder, tableElement, daoClassName, STATEMENT_VARIABLE);
      if (!GENERATE_LOGGING && !isIdSettingNeeded(tableElement)) {
        addInlineExecuteInsertWithCheckIdValidity(builder, STATEMENT_VARIABLE, FAILED_TO_INSERT_ERR_MSG);
      } else {
        builder.addStatement("final long id = $L.executeInsert()", STATEMENT_VARIABLE);
        addAfterInsertLoggingStatement(builder);
        addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
        addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
      }
    } else {
      addBindToInsertStatement(builder, tableElement, daoClassName, STATEMENT_VARIABLE);
      builder.addStatement("final long id = $L.executeInsert()", STATEMENT_VARIABLE);
      addAfterInsertLoggingStatement(builder);
      addIdValidityRespectingConflictAbort(builder, tableElement, daoClassName, FAILED_TO_INSERT_ERR_MSG);
    }
  }

  private void addBulkInsertComplexColumnIgnoreConflictLoopBody(MethodSpec.Builder builder) {
    builder.addStatement("final $T $L = $L.newTransaction()",
        TRANSACTION, TRANSACTION_VARIABLE, DB_CONNECTION_VARIABLE)
        .beginControlFlow("try");
    addCallToInternalInsertOnComplexColumnsIfNeeded(entityEnvironment, builder);
    addBindToInsertStatement(builder, tableElement, daoClassName, STATEMENT_VARIABLE);
    builder.addStatement("final long id = $L.executeInsert()", STATEMENT_VARIABLE);
    addAfterInsertLoggingStatement(builder);
    builder.beginControlFlow("if (id != -1)");
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
    builder.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
        .addStatement("atLeastOneSuccess = true")
        .endControlFlow()
        .nextControlFlow("catch ($T e)", OPERATION_FAILED_EXCEPTION)
        .addStatement("continue")
        .nextControlFlow("finally")
        .addStatement("$L.end()", TRANSACTION_VARIABLE)
        .endControlFlow();
  }

  private void addCallToInternalInsertOnComplexColumnsIfNeeded(EntityEnvironment entityEnvironment,
                                                               MethodSpec.Builder builder) {
    if (tableElement.hasAnyPersistedComplexColumns()) {
      CodeBlock.Builder codeBuilder = CodeBlock.builder();
      if (tableElement.hasAnyPersistedImmutableComplexColumns()) {
        codeBuilder.add("final long[] ids = ");
      }
      codeBuilder.add("$T.$L($L, $L.getDbConnection(), $L)",
          entityEnvironment.getDaoClassName(),
          METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS,
          ENTITY_VARIABLE,
          MANAGER_VARIABLE,
          OPERATION_HELPER_VARIABLE)
          .add(codeBlockEnd());
      builder.addCode(codeBuilder.build());
    }
  }

  private void addInsertLoggingStatement(MethodSpec.Builder builder, TableElement tableElement) {
    if (GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"INSERT\\n  table: $L\\n  object: %s\", $L.toString())",
          SQLITE_MAGIC, LOG_UTIL, tableElement.getTableName(), ENTITY_VARIABLE);
    }
  }

  static void addAfterInsertLoggingStatement(MethodSpec.Builder builder) {
    if (GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"INSERT id: %s\", id)", SQLITE_MAGIC, LOG_UTIL);
    }
  }
}
