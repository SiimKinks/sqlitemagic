package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
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
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_PERSIST_ERR_MSG;
import static com.siimkinks.sqlitemagic.WriterUtil.CHECK_RESULT;
import static com.siimkinks.sqlitemagic.WriterUtil.CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_PERSIST_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_PERSIST_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.LOG_UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.OPERATION_FAILED_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_DATABASE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_MAGIC;
import static com.siimkinks.sqlitemagic.WriterUtil.addCallableToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableCreateFromParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxCompletableFromEmitterToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromCallableParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.dbConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.dbVariableFromPresentConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerVariableFromDbConnection;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.ifDisposed;
import static com.siimkinks.sqlitemagic.WriterUtil.insertStatementVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxCompletableMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.WriterUtil.updateStatementVariable;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_NOT_NULL_CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_PERSIST_IGNORE_NULL_INTERNAL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_PERSIST_INTERNAL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_IGNORE_NULL_VALUES;
import static com.siimkinks.sqlitemagic.writer.InsertWriter.addBindToInsertStatement;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCallToComplexColumnsOperationWithContentValuesIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addContentValuesAndDbVariables;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addInlineExecuteInsertWithCheckIdValidity;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addMethodInternalCallOnComplexColumnsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxCompletableEmitterTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addSetIdStatementIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addDisposableForEmitter;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addThrowOperationFailedExceptionWithEntityVariable;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTopMethodStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.isIdSettingNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.statementWithImmutableIdsIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;

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
    final MethodSpec internalPersist = persistInternal();
    final MethodSpec internalPersistIgnoringNull = persistIgnoringNullInternal();
    classBuilder.addMethod(internalPersistIgnoringNull)
        .addMethod(internalPersist)
        .addType(persist(internalPersist, internalPersistIgnoringNull))
        .addType(bulkPersist());
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private void addPersistIgnoringNullValuesMethodInternalCallOnComplexColumnsIdNeeded(TypeSpec.Builder daoClassBuilder) {
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS,
        new ReturnCallback<String, ColumnElement>() {
          @Override
          public String call(ColumnElement obj) {
            return METHOD_PERSIST_IGNORE_NULL_INTERNAL;
          }
        },
        ParameterSpec.builder(CONTENT_VALUES, "values").build(),
        ParameterSpec.builder(SQLITE_DATABASE, "db").build());
  }

  private void addPersistMethodInternalCallOnComplexColumnsIdNeeded(TypeSpec.Builder daoClassBuilder) {
    addMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS,
        COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
        new ReturnCallback<String, ColumnElement>() {
          @Override
          public String call(ColumnElement obj) {
            return METHOD_PERSIST_INTERNAL;
          }
        },
        connectionImplParameter());
  }

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private TypeSpec persist(MethodSpec persist, MethodSpec persistIgnoringNull) {
    final MethodSpec persistExecute = persistExecute(persist, persistIgnoringNull);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_PERSIST, ENTITY_PERSIST_BUILDER, tableElementTypeName, ENTITY_VARIABLE);
    return builder
        .addSuperinterface(ENTITY_PERSIST_BUILDER)
        .addField(boolean.class, IGNORE_NULL_VALUES_VARIABLE, Modifier.PRIVATE)
        .addMethod(setIgnoreNullValues(ENTITY_PERSIST_BUILDER))
        .addMethod(persistExecute)
        .addMethod(persistObserve(builder, persistExecute))
        .build();
  }

  private MethodSpec persistExecute(MethodSpec persist, MethodSpec persistIgnoringNull) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.LONG)
        .addCode(dbConnectionVariable());
    final boolean hasComplexColumns = tableElement.hasAnyPersistedComplexColumns();
    addTopMethodStartBlock(builder, hasComplexColumns);

    builder.addStatement("final $T id", TypeName.LONG)
        .beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE)
        .addStatement("final $T values = new $T()", CONTENT_VALUES, CONTENT_VALUES)
        .addCode(dbVariableFromPresentConnectionVariable())
        .addStatement("id = $N($L, values, db)", persistIgnoringNull, ENTITY_VARIABLE);

    builder.nextControlFlow("else");

    builder.addCode(entityDbManagerVariableFromDbConnection(tableElement));
    if (tableElement.isImmutable()) {
      builder.addStatement("id = $N($L, $L)", persist, ENTITY_VARIABLE, MANAGER_VARIABLE);
    } else {
      builder.addStatement("$N($L, $L)", persist, ENTITY_VARIABLE, MANAGER_VARIABLE)
          .addStatement("id = $T.$N($L)", daoClassName, entityEnvironment.getEntityIdGetter(), ENTITY_VARIABLE);
    }

    builder.endControlFlow();

    final String returnStatement = "return id";
    final String failReturnStatement = "return -1";
    addTopMethodEndBlock(builder, allTableTriggers, hasComplexColumns, returnStatement, failReturnStatement);
    return builder.build();
  }

  private MethodSpec persistObserve(TypeSpec.Builder typeBuilder, final MethodSpec persistExecute) {
    final TypeName entityTypeName = TypeName.LONG.box();
    final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
        .addAnnotation(Override.class);
    addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addStatement("final $T id = $N()", TypeName.LONG, persistExecute)
            .beginControlFlow("if (id == -1)")
            .addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, FAILED_TO_PERSIST_ERR_MSG)
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

  private MethodSpec persistInternal() {
    final MethodSpec executeInsert = insertWriter.getExecuteInsert();
    final boolean idColumnNullable = tableElement.getIdColumn().isNullable();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_PERSIST_INTERNAL)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(entityParameter(tableElementTypeName))
        .addParameter(entityDbManagerParameter());
    final String returnStatement;
    if (tableElement.isImmutable()) {
      returnStatement = "return ";
      builder.returns(TypeName.LONG);
    } else {
      returnStatement = "";
    }
    addCallToComplexColumnsPersistIfNeeded(builder);
    addPersistLoggingStatement(builder);
    builder.addStatement("int rowsAffected = 0");
    if (idColumnNullable) {
      builder.addCode(entityEnvironment.getFinalIdVariable())
          .beginControlFlow("if (id != null)");
    }
    builder.addCode(updateStatementVariable());
    builder.beginControlFlow("synchronized (stm)");
    addBindToUpdateStatement(builder, "stm");
    builder.addStatement("rowsAffected = stm.executeUpdateDelete()");
    builder.endControlFlow();
    if (idColumnNullable) {
      builder.endControlFlow();
    }
    builder.beginControlFlow("if (rowsAffected <= 0)");
    addPersistUpdateFailedLoggingStatement(builder);
    builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, returnStatement + "$T.$N($L, $L", entityEnvironment.getHandlerClassName(), executeInsert, ENTITY_VARIABLE, MANAGER_VARIABLE))
        .endControlFlow();
    if (tableElement.isImmutable()) {
      builder.addStatement("return $T.$N($L)", daoClassName, entityEnvironment.getEntityIdGetter(), ENTITY_VARIABLE);
    }
    return builder.build();
  }

  private void addBindToUpdateStatement(MethodSpec.Builder builder, String updateStmVariableName) {
    final String bindMethodName = tableElement.hasAnyPersistedImmutableComplexColumns() ? METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS : METHOD_BIND_TO_UPDATE_STATEMENT;
    builder.addCode(statementWithImmutableIdsIfNeeded(tableElement,
        "$T.$L($L, $L$L", daoClassName, bindMethodName, updateStmVariableName, ENTITY_VARIABLE,
        tableElement.getIdColumn().isNullable() ? ", id" : ""));
  }

  private void addCallToComplexColumnsPersistIfNeeded(MethodSpec.Builder builder) {
    addCallToComplexColumnsOperationWithContentValuesIfNeeded(builder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS, MANAGER_VARIABLE + ".getDbConnection()");
  }

  private MethodSpec persistIgnoringNullInternal() {
    final String tableName = tableElement.getTableName();
    final ColumnElement idColumn = tableElement.getIdColumn();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_PERSIST_IGNORE_NULL_INTERNAL)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addParameter(CONTENT_VALUES, "values")
        .addParameter(SQLITE_DATABASE, "db")
        .returns(TypeName.LONG);
    addCallToComplexColumnsPersistIgnoringNullIfNeeded(builder);
    addPersistLoggingStatement(builder);
    final FormatData whereIdStatementPart = entityEnvironment.getWhereIdStatementPartWithProvidedIdVariable("id");
    addBindToNotNullValues(builder);
    builder.addCode(entityEnvironment.getIdVariable());
    addUpdateExecuteInControlFlow(tableName, idColumn, builder, whereIdStatementPart);
    addPersistUpdateFailedLoggingStatement(builder);
    if (idColumn.isAutoincrementId()) {
      builder.addStatement("values.remove($S)", idColumn.getColumnName());
    }
    builder.addStatement("id = db.insertWithOnConflict($S, null, values, SQLiteDatabase.CONFLICT_ABORT)", tableName);
    addPersistAfterInsertLoggingStatement(builder);
    addCheckIdValidity(builder, FAILED_TO_PERSIST_ERR_MSG);
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
    builder.endControlFlow()
        .addStatement("return id");
    return builder.build();
  }

  private void addCallToComplexColumnsPersistIgnoringNullIfNeeded(MethodSpec.Builder builder) {
    addCallToComplexColumnsOperationWithContentValuesIfNeeded(builder, entityEnvironment, METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS, "values", "db");
  }

  @NonNull
  private MethodSpec.Builder addBindToNotNullValues(MethodSpec.Builder builder) {
    return builder.addCode(statementWithImmutableIdsIfNeeded(tableElement, "$T.$L($L, values", daoClassName, METHOD_BIND_TO_NOT_NULL_CONTENT_VALUES, ENTITY_VARIABLE));
  }

  private void addUpdateExecuteInControlFlow(String tableName, ColumnElement idColumn, MethodSpec.Builder builder, FormatData whereIdStatementPart) {
    builder.beginControlFlow(String.format("if ($Ldb.updateWithOnConflict($S, values, %s, SQLiteDatabase.CONFLICT_ABORT) <= 0)", whereIdStatementPart.getFormat()),
        whereIdStatementPart.getWithOtherArgsBefore(
            idColumn.isNullable() ? "id == null || " : "",
            tableName));
  }

  private TypeSpec bulkPersist() {
    final ParameterizedTypeName interfaceType = ParameterizedTypeName.get(ENTITY_BULK_PERSIST_BUILDER, tableElementTypeName);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_PERSIST, interfaceType, iterable, OBJECTS_VARIABLE);
    return builder
        .addSuperinterface(interfaceType)
        .addField(boolean.class, IGNORE_NULL_VALUES_VARIABLE, Modifier.PRIVATE)
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
        builder.addCode(dbConnectionVariable());
        addDisposableForEmitter(builder);
        addTransactionStartBlock(builder);

        builder.beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE);
        addBulkPersistIgnoreNullTopBlock(builder, true);
        addBulkPersistIgnoreNullInsertBlock(builder, false);
        builder.endControlFlow();
        addBulkPersistOnNext(builder);
        builder.endControlFlow();

        builder.nextControlFlow("else");
        final boolean idColumnNullable = tableElement.getIdColumn().isNullable();
        builder.addCode(entityDbManagerVariableFromDbConnection(tableElement))
            .addCode(updateStatementVariable("updateStm"))
            .addCode(insertStatementVariable("insertStm"))
            .beginControlFlow("synchronized (updateStm)")
            .beginControlFlow("synchronized (insertStm)")
            .beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
        addBulkPersistTopBlock(idColumnNullable, builder);
        addBulkPersistMainInsertExecuteBlock(idColumnNullable, builder);
        builder.endControlFlow();
        addBulkPersistOnNext(builder);
        builder.endControlFlow()
            .endControlFlow()
            .endControlFlow();

        builder.endControlFlow();

        addRxCompletableEmitterTransactionEndBlock(builder, allTableTriggers);
      }
    });
    return builder.build();
  }

  private void addBulkPersistOnNext(MethodSpec.Builder builder) {
    builder.beginControlFlow(ifDisposed())
        .addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, ERROR_UNSUBSCRIBED_UNEXPECTEDLY)
        .endControlFlow();
  }

  private MethodSpec bulkPersistExecute() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addCode(dbConnectionVariable());

    final boolean idNullable = tableElement.getIdColumn().isNullable();
    addTransactionStartBlock(builder);

    builder.beginControlFlow("if ($N)", IGNORE_NULL_VALUES_VARIABLE);
    addBulkPersistIgnoreNullTopBlock(builder, idNullable);
    if (isIdSettingNeeded(tableElement)) {
      addBulkPersistIgnoreNullInsertBlock(builder, !idNullable);
    } else {
      builder.beginControlFlow("if (db.insertWithOnConflict($S, null, values, SQLiteDatabase.CONFLICT_ABORT) == -1)", tableElement.getTableName());
      addThrowOperationFailedExceptionWithEntityVariable(builder, FAILED_TO_PERSIST_ERR_MSG);
      builder.endControlFlow();
    }
    builder.endControlFlow()
        .endControlFlow();

    builder.nextControlFlow("else");

    builder.addCode(entityDbManagerVariableFromDbConnection(tableElement))
        .addCode(updateStatementVariable("updateStm"))
        .addCode(insertStatementVariable("insertStm"))
        .beginControlFlow("synchronized (updateStm)")
        .beginControlFlow("synchronized (insertStm)")
        .beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addBulkPersistTopBlock(idNullable, builder);
    if (isIdSettingNeeded(tableElement)) {
      addBulkPersistMainInsertExecuteBlock(idNullable, builder);
    } else {
      addInlineExecuteInsertWithCheckIdValidity(builder, "insertStm", FAILED_TO_PERSIST_ERR_MSG);
    }
    builder.endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .endControlFlow();

    builder.endControlFlow();

    addTransactionEndBlock(builder, allTableTriggers, "return true", "return false");
    return builder.build();
  }

  private void addBulkPersistIgnoreNullInsertBlock(MethodSpec.Builder builder, boolean idInSeparateVariable) {
    CodeBlock.Builder insertBuilder = CodeBlock.builder();
    if (idInSeparateVariable) {
      insertBuilder.add("final long ");
    }
    insertBuilder.add("id = db.insertWithOnConflict($S, null, values, SQLiteDatabase.CONFLICT_ABORT)", tableElement.getTableName())
        .add(codeBlockEnd());
    builder.addCode(insertBuilder.build());
    addPersistAfterInsertLoggingStatement(builder);
    addCheckIdValidity(builder, FAILED_TO_PERSIST_ERR_MSG);
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
  }

  private void addBulkPersistIgnoreNullTopBlock(MethodSpec.Builder builder, boolean idInSeparateVariable) {
    final ColumnElement idColumn = tableElement.getIdColumn();
    addContentValuesAndDbVariables(builder);
    builder.beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
    addCallToComplexColumnsPersistIgnoringNullIfNeeded(builder);
    addPersistLoggingStatement(builder);
    addBindToNotNullValues(builder);
    final FormatData whereIdStatementPart;
    if (idInSeparateVariable) {
      builder.addCode(entityEnvironment.getIdVariable());
      whereIdStatementPart = entityEnvironment.getWhereIdStatementPartWithProvidedIdVariable("id");
    } else {
      whereIdStatementPart = entityEnvironment.getWhereIdStatementPart();
    }
    addUpdateExecuteInControlFlow(tableElement.getTableName(), idColumn, builder, whereIdStatementPart);
    addPersistUpdateFailedLoggingStatement(builder);
    if (idColumn.isAutoincrementId()) {
      builder.addStatement("values.remove($S)", idColumn.getColumnName());
    }
  }

  private void addBulkPersistMainInsertExecuteBlock(boolean idColumnNullable, MethodSpec.Builder builder) {
    if (idColumnNullable) {
      builder.addStatement("id = insertStm.executeInsert()");
    } else {
      builder.addStatement("final $T id = insertStm.executeInsert()", TypeName.LONG);
    }
    addCheckIdValidity(builder, FAILED_TO_INSERT_ERR_MSG);
    addSetIdStatementIfNeeded(tableElement, daoClassName, builder);
  }

  private void addBulkPersistTopBlock(boolean idColumnNullable, MethodSpec.Builder builder) {
    addCallToComplexColumnsPersistIfNeeded(builder);
    addPersistLoggingStatement(builder);
    if (idColumnNullable) {
      builder.addStatement("int rowsAffected = 0")
          .addCode(entityEnvironment.getIdVariable())
          .beginControlFlow("if (id != null)");
    }
    addBindToUpdateStatement(builder, "updateStm");
    if (idColumnNullable) {
      builder.addStatement("rowsAffected = updateStm.executeUpdateDelete()")
          .endControlFlow()
          .beginControlFlow("if (rowsAffected <= 0)");
    } else {
      builder.beginControlFlow("if (updateStm.executeUpdateDelete() <= 0)");
    }
    addPersistUpdateFailedLoggingStatement(builder);
    addBindToInsertStatement(builder, tableElement, daoClassName, "insertStm");
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
