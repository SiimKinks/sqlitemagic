package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder;
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder;
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder;
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder;
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder;
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder;
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder;
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder;
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder;
import com.siimkinks.sqlitemagic.exception.OperationFailedException;
import com.siimkinks.sqlitemagic.internal.MutableInt;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.internal.StringArraySet;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.StringUtil;
import com.siimkinks.sqlitemagic.writer.EntityEnvironment;
import com.siimkinks.sqlitemagic.writer.Operation;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.processing.Filer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import static com.siimkinks.sqlitemagic.Const.PRIMITIVES_DEFAULT_VALUE_MAP;
import static com.siimkinks.sqlitemagic.Const.PRIVATE_FINAL_FIELD_MODIFIERS;
import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_INSERT_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_UPDATE_SQL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BY_COLUMN;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CONNECTION_PROVIDER;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CREATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_GET_INSERT_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_GET_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_OBSERVE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_CONFLICT_ALGORITHM;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.tableNameFromStructureConstant;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_BY_COLUMNS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.CONFLICT_ALGORITHM_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DISPOSABLE_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.EMITTER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OPERATION_HELPER_VARIABLE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

public class WriterUtil {

  public static final ClassName SQLITE_DATABASE = ClassName.get("android.database.sqlite", "SQLiteDatabase");
  public static final ClassName SUPPORT_SQLITE_DATABASE = ClassName.get("android.arch.persistence.db", "SupportSQLiteDatabase");
  public static final ClassName SUPPORT_SQLITE_STATEMENT = ClassName.get("android.arch.persistence.db", "SupportSQLiteStatement");
  public static final ClassName SQL_EXCEPTION = ClassName.get("android.database", "SQLException");
  public static final ClassName CHECK_RESULT = ClassName.get("android.support.annotation", "CheckResult");
  public static final ClassName NON_NULL = ClassName.get("android.support.annotation", "NonNull");
  public static final ClassName NULLABLE = ClassName.get("android.support.annotation", "Nullable");

  public static final ClassName ITERABLE = ClassName.get(Iterable.class);
  public static final ClassName COLLECTION = ClassName.get(Collection.class);
  public static final ClassName COLLECTIONS = ClassName.get(Collections.class);
  public static final ClassName LIST = ClassName.get(List.class);
  public static final ClassName ARRAY_LIST = ClassName.get(ArrayList.class);
  public static final ClassName STRING = ClassName.get(String.class);
  public static final ClassName STRING_BUILDER = ClassName.get(StringBuilder.class);
  public static final ClassName NUMBER = ClassName.get(Number.class);
  public static final ClassName CHAR_SEQUENCE = ClassName.get(CharSequence.class);
  public static final ClassName CALLABLE = ClassName.get(Callable.class);

  public static final ClassName UTIL = ClassName.get(Utils.class);
  public static final ClassName LOG_UTIL = ClassName.get(LogUtil.class);
  public static final ClassName SQL_UTIL = ClassName.get(SqlUtil.class);
  public static final ClassName SQLITE_MAGIC = ClassName.get(SqliteMagic.class);
  public static final ClassName DB_CONNECTION = ClassName.get(DbConnection.class);
  public static final ClassName DB_CONNECTION_IMPL = ClassName.get(DbConnectionImpl.class);
  public static final ClassName OPERATION_FAILED_EXCEPTION = ClassName.get(OperationFailedException.class);
  public static final ClassName TRANSACTION = ClassName.get(Transaction.class);
  public static final ClassName MAPPER = ClassName.get(Query.Mapper.class);
  public static final ClassName MAPPER_WITH_COLUMN_OFFSET = ClassName.get(Query.MapperWithColumnOffset.class);
  public static final ClassName MUTABLE_INT = ClassName.get(MutableInt.class);
  public static final ClassName FROM = ClassName.get(Select.From.class);
  public static final ClassName TABLE = ClassName.get(Table.class);
  public static final ClassName COLUMN = ClassName.get(Column.class);
  public static final ClassName NUMERIC_COLUMN = ClassName.get(NumericColumn.class);
  public static final ClassName UNIQUE_COLUMN = ClassName.get(UniqueColumn.class);
  public static final ClassName UNIQUE_NUMERIC_COLUMN = ClassName.get(UniqueNumericColumn.class);
  public static final ClassName COMPLEX_COLUMN = ClassName.get(ComplexColumn.class);
  public static final ClassName UNIQUE = ClassName.get(Unique.class);
  public static final ClassName NULLABLE_COLUMN = ClassName.get(com.siimkinks.sqlitemagic.Nullable.class);
  public static final ClassName NOT_NULLABLE_COLUMN = ClassName.get(NotNullable.class);
  public static final ClassName JOIN_CLAUSE = ClassName.get(JoinClause.class);
  public static final ClassName CURSOR = ClassName.get("android.database", "Cursor");
  public static final ClassName SIMPLE_ARRAY_MAP = ClassName.get(SimpleArrayMap.class);
  public static final ClassName STRING_ARRAY_SET = ClassName.get(StringArraySet.class);
  public static final ClassName COMPILED_N_COLUMNS_SELECT_IMPL = ClassName.get(CompiledSelectImpl.class);
  public static final ClassName COMPILED_N_COLUMNS_SELECT = ClassName.get(CompiledSelect.class);
  public static final ClassName OPERATION_HELPER = ClassName.get(OperationHelper.class);
  public static final ClassName VARIABLE_ARGS_OPERATION_HELPER = ClassName.get(VariableArgsOperationHelper.class);
  public static final ClassName ENTITY_DB_MANAGER = ClassName.get(EntityDbManager.class);
  public static final ClassName ENTITY_DELETE_BUILDER = ClassName.get(EntityDeleteBuilder.class);
  public static final ClassName ENTITY_INSERT_BUILDER = ClassName.get(EntityInsertBuilder.class);
  public static final ClassName ENTITY_UPDATE_BUILDER = ClassName.get(EntityUpdateBuilder.class);
  public static final ClassName ENTITY_PERSIST_BUILDER = ClassName.get(EntityPersistBuilder.class);
  public static final ClassName ENTITY_DELETE_TABLE_BUILDER = ClassName.get(EntityDeleteTableBuilder.class);
  public static final ClassName ENTITY_BULK_INSERT_BUILDER = ClassName.get(EntityBulkInsertBuilder.class);
  public static final ClassName ENTITY_BULK_UPDATE_BUILDER = ClassName.get(EntityBulkUpdateBuilder.class);
  public static final ClassName ENTITY_BULK_PERSIST_BUILDER = ClassName.get(EntityBulkPersistBuilder.class);
  public static final ClassName ENTITY_BULK_DELETE_BUILDER = ClassName.get(EntityBulkDeleteBuilder.class);
  public static final ParameterizedTypeName LIST_JOIN_CLAUSE_TYPE_NAME = ParameterizedTypeName.get(ArrayList.class, JoinClause.class);
  public static final ParameterizedTypeName BIND_VALUES_MAP = ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, ClassName.get(String.class), TypeName.OBJECT);
  public static final TypeName VALUE_PARSER = WildcardTypeName.get(Utils.ValueParser.class);
  public static final TypeName OPERATION_BY_COLUMNS = ParameterizedTypeName.get(ARRAY_LIST, COLUMN);

  public static final ClassName SINGLE = ClassName.get("io.reactivex", "Single");
  public static final ClassName COMPLETABLE = ClassName.get("io.reactivex", "Completable");
  public static final ClassName COMPLETABLE_ON_SUBSCRIBE = ClassName.get("io.reactivex", "CompletableOnSubscribe");
  public static final ClassName COMPLETABLE_EMITTER = ClassName.get("io.reactivex", "CompletableEmitter");
  public static final ClassName ACTION = ClassName.get("io.reactivex.functions", "Action");
  public static final ClassName DISPOSABLE = ClassName.get("io.reactivex.disposables", "Disposable");
  public static final ClassName DISPOSABLES = ClassName.get("io.reactivex.disposables", "Disposables");

  public static final String IF_LOGGING_ENABLED = "if (SqliteMagic.LOGGING_ENABLED)";

  public static FormatData newInstanceField(String entityElementVariableName, TypeName typeName) {
    return FormatData.create(String.format("$T %s = new $T()", entityElementVariableName), typeName, typeName);
  }

  public static TypeName typedIterable(TypeName typeName) {
    return ParameterizedTypeName.get(ITERABLE, typeName);
  }

  public static TypeName typedCollection(TypeName typeName) {
    return ParameterizedTypeName.get(COLLECTION, typeName);
  }

  public static WildcardTypeName anyWildcardTypeName() {
    return WildcardTypeName.subtypeOf(Object.class);
  }

  public static TypeName typeNameForGenerics(@NonNull ExtendedTypeElement type) {
    final TypeName typeName = TypeName.get(type.getTypeMirror());
    return type.isPrimitiveElement() ? typeName.box() : typeName;
  }

  public static TypeName typeName(@NonNull ExtendedTypeElement type) {
    return TypeName.get(type.getTypeMirror());
  }

  public static TypeName typeName(@NonNull TypeMirror type) {
    return TypeName.get(type);
  }

  public static MethodSpec.Builder createMagicInvokableMethod(String className, String methodName) {
    return MethodSpec.methodBuilder(methodName);
  }

  public static void addTableTriggersSendingStatement(MethodSpec.Builder builder, Set<TableElement> allTableTriggers) {
    if (allTableTriggers.size() > 1) {
      final List<Object> args = new ArrayList<>();
      args.add(DB_CONNECTION_VARIABLE);
      final String joinedTableTriggers = StringUtil.join(", ", allTableTriggers, new StringUtil.ToStringCallback<TableElement>() {
        @NonNull
        @Override
        public String toString(@NonNull TableElement tableElement) {
          final FormatData tableName = tableNameFromStructureConstant(tableElement);
          Collections.addAll(args, tableName.getArgs());
          return tableName.getFormat();
        }
      });
      builder.addStatement("$L.sendTableTriggers(" + joinedTableTriggers + ")", args.toArray());
    } else {
      final TableElement firstValue = allTableTriggers.iterator().next();
      final FormatData tableName = tableNameFromStructureConstant(firstValue);
      builder.addStatement(tableName.formatInto("$L.sendTableTrigger(%s)"), tableName.getWithOtherArgsBefore(DB_CONNECTION_VARIABLE));
    }
  }

  public static MethodSpec buildSqlTransactionMethod(MethodSpec.Builder methodBuilder,
                                                     CodeBlock sqlTransactionBody) {
    return buildSqlTransactionMethod(methodBuilder, sqlTransactionBody, null, false);
  }

  public static MethodSpec buildSqlTransactionMethod(MethodSpec.Builder methodBuilder,
                                                     CodeBlock sqlTransactionBody,
                                                     @Nullable CodeBlock returnBody,
                                                     boolean throwError) {
    methodBuilder
        .addModifiers(Const.STATIC_METHOD_MODIFIERS)
        .addParameter(SUPPORT_SQLITE_DATABASE, "db")
        .addStatement("db.beginTransaction()")
        .beginControlFlow("try")
        .addCode(sqlTransactionBody)
        .addStatement("db.setTransactionSuccessful()");
    if (returnBody != null) {
      methodBuilder.addCode(returnBody);
    }
    methodBuilder.nextControlFlow("catch (Exception e)");
    methodBuilder.addStatement("$L $T.logError(e, \"Error while executing db transaction\")", IF_LOGGING_ENABLED, LOG_UTIL);
    if (throwError) {
      methodBuilder.addStatement("throw e");
    }
    return methodBuilder
        .nextControlFlow("finally")
        .addStatement("db.endTransaction()")
        .endControlFlow()
        .build();
  }

  public static MethodSpec.Builder operationRxSingleMethod(TypeName entityTypeName) {
    return operationRxSingleMethod(METHOD_OBSERVE, entityTypeName);
  }

  public static MethodSpec.Builder operationRxSingleMethod(String methodName, TypeName entityTypeName) {
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(NON_NULL)
        .addAnnotation(CHECK_RESULT)
        .returns(ParameterizedTypeName.get(SINGLE, entityTypeName));
  }

  public static MethodSpec.Builder operationRxCompletableMethod() {
    return operationRxCompletableMethod(METHOD_OBSERVE);
  }

  public static MethodSpec.Builder operationRxCompletableMethod(String methodName) {
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(NON_NULL)
        .addAnnotation(CHECK_RESULT)
        .returns(COMPLETABLE);
  }

  public static void addRxSingleCreateFromCallableParentClass(MethodSpec.Builder builder) {
    builder.addCode("return $T.fromCallable(this)", SINGLE)
        .addCode(chainedScheduler());
  }

  public static void addRxCompletableFromParentClass(MethodSpec.Builder builder) {
    builder.addCode("return $T.fromAction(this)", COMPLETABLE)
        .addCode(chainedScheduler());
  }

  public static void addRxCompletableCreateFromParentClass(MethodSpec.Builder builder) {
    builder.addCode("return $T.create(this)", COMPLETABLE)
        .addCode(chainedScheduler());
  }

  public static CodeBlock chainedScheduler() {
    return CodeBlock.builder()
        .add(";\n")
        .build();
  }

  public static String ifDisposed() {
    return "if (" + DISPOSABLE_VARIABLE + ".isDisposed())";
  }

  public static String emitterOnComplete() {
    return EMITTER_VARIABLE + ".onComplete()";
  }

  public static CodeBlock emitterOnError() {
    return CodeBlock.builder()
        .beginControlFlow("if (!$L.isDisposed())", DISPOSABLE_VARIABLE)
        .addStatement("$L.onError(e)", EMITTER_VARIABLE)
        .endControlFlow()
        .build();
  }

  public static TypeSpec.Builder addRxCompletableFromEmitterToType(@NonNull TypeSpec.Builder typeBuilder,
                                                                   @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
    final MethodSpec.Builder methodBuilder = MethodSpec
        .methodBuilder("subscribe")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(COMPLETABLE_EMITTER, EMITTER_VARIABLE)
        .addException(Exception.class);
    methodBodyBuildCallback.call(methodBuilder);

    return typeBuilder
        .addSuperinterface(COMPLETABLE_ON_SUBSCRIBE)
        .addMethod(methodBuilder.build());
  }

  public static TypeSpec.Builder addCallableToType(@NonNull TypeSpec.Builder typeBuilder,
                                                   @NonNull TypeName returnType,
                                                   @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
    final MethodSpec.Builder callBuilder = MethodSpec.methodBuilder("call")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addException(Exception.class)
        .returns(returnType);
    methodBodyBuildCallback.call(callBuilder);

    typeBuilder.addSuperinterface(ParameterizedTypeName.get(CALLABLE, returnType))
        .addMethod(callBuilder.build());
    return typeBuilder;
  }

  public static TypeSpec.Builder addRxActionToType(@NonNull TypeSpec.Builder typeBuilder,
                                                   @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
    final MethodSpec.Builder callBuilder = MethodSpec.methodBuilder("run")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addException(Exception.class);
    methodBodyBuildCallback.call(callBuilder);

    typeBuilder.addSuperinterface(ACTION)
        .addMethod(callBuilder.build());
    return typeBuilder;
  }

  public static TypeSpec.Builder operationBuilderInnerClassSkeleton(EntityEnvironment entityEnvironment,
                                                                    String className,
                                                                    TypeName classInterfaceName,
                                                                    TypeName mainObjectType,
                                                                    String mainObjectVariableName) {
    final TypeName createdClassTypeName = getHandlerInnerClassName(entityEnvironment, className);
    return TypeSpec.classBuilder(className)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .addField(mainObjectType, mainObjectVariableName, PRIVATE_FINAL_FIELD_MODIFIERS)
        .addField(FieldSpec.builder(DB_CONNECTION_IMPL, DB_CONNECTION_VARIABLE, PRIVATE)
            .addAnnotation(NULLABLE)
            .build())
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameter(notNullParameter(mainObjectType, mainObjectVariableName))
            .addStatement("this.$N = $N", mainObjectVariableName, mainObjectVariableName)
            .build())
        .addMethod(createMagicInvokableMethod(EntityEnvironment.getGeneratedHandlerInnerClassName(entityEnvironment.getTableElement(), className), METHOD_CREATE)
            .addModifiers(STATIC_METHOD_MODIFIERS)
            .addAnnotation(CHECK_RESULT)
            .addParameter(notNullParameter(mainObjectType, mainObjectVariableName))
            .addStatement("return new $N($N)", className, mainObjectVariableName)
            .returns(createdClassTypeName)
            .build())
        .addMethod(connectionProviderMethod(classInterfaceName));
  }

  @NonNull
  public static MethodSpec connectionProviderMethod(TypeName classInterfaceName) {
    return MethodSpec.methodBuilder(METHOD_CONNECTION_PROVIDER)
        .addModifiers(PUBLIC)
        .addAnnotation(NON_NULL)
        .addAnnotation(Override.class)
        .addParameter(notNullParameter(DB_CONNECTION, "connection"))
        .addStatement("this.$L = ($T) connection", DB_CONNECTION_VARIABLE, DB_CONNECTION_IMPL)
        .addStatement("return this")
        .returns(classInterfaceName)
        .build();
  }

  public static ClassName getHandlerInnerClassName(EntityEnvironment entityEnvironment, String className) {
    return ClassName.get(PACKAGE_ROOT, entityEnvironment.getHandlerClassNameString(), className);
  }

  public static ParameterSpec notNullParameter(TypeName typeName, String name) {
    return ParameterSpec.builder(typeName, name)
        .addAnnotation(NON_NULL)
        .build();
  }

  public static ParameterSpec notNullParameter(Type type, String name) {
    return ParameterSpec.builder(type, name)
        .addAnnotation(NON_NULL)
        .build();
  }

  public static ParameterSpec nullableParameter(TypeName typeName, String name) {
    return ParameterSpec.builder(typeName, name)
        .addAnnotation(NULLABLE)
        .build();
  }

  public static ParameterSpec nullableParameter(Type type, String name) {
    return ParameterSpec.builder(type, name)
        .addAnnotation(NULLABLE)
        .build();
  }

  public static CodeBlock dbConnectionVariable() {
    return CodeBlock.builder()
        .addStatement("final $1T $2L = this.$2L != null ? this.$2L : $3T.getDefaultDbConnection()",
            DB_CONNECTION_IMPL,
            DB_CONNECTION_VARIABLE,
            SQLITE_MAGIC)
        .build();
  }

  public static CodeBlock entityDbManagerVariableFromDbConnection(TableElement tableElement) {
    return CodeBlock.builder()
        .addStatement("final $T $L = $L.getEntityDbManager($S, $L)",
            ENTITY_DB_MANAGER,
            MANAGER_VARIABLE,
            DB_CONNECTION_VARIABLE,
            tableElement.getEnvironment().getModuleName(),
            tableElement.getTablePos())
        .build();
  }

  public static CodeBlock entityDbVariablesForOperationBuilder(TableElement tableElement) {
    return CodeBlock.builder()
        .add(dbConnectionVariable())
        .add(entityDbManagerVariableFromDbConnection(tableElement))
        .build();
  }

  public static CodeBlock opHelperVariable(Operation op) {
    return CodeBlock.builder()
        .addStatement("final $1T $2L = new $1T($3L, $4L, null)",
            OPERATION_HELPER,
            OPERATION_HELPER_VARIABLE,
            CONFLICT_ALGORITHM_VARIABLE,
            op.ordinal())
        .build();
  }

  public static CodeBlock opByColumnHelperVariable(Operation op) {
    return CodeBlock.builder()
        .addStatement("final $1T $2L = new $1T($3L, $4L, $5L)",
            OPERATION_HELPER,
            OPERATION_HELPER_VARIABLE,
            CONFLICT_ALGORITHM_VARIABLE,
            op.ordinal(),
            OPERATION_BY_COLUMNS_VARIABLE)
        .build();
  }

  public static CodeBlock variableArgsOpHelperVariable() {
    return CodeBlock.builder()
        .addStatement("final $T $L = new $T($L)",
            VARIABLE_ARGS_OPERATION_HELPER,
            OPERATION_HELPER_VARIABLE,
            VARIABLE_ARGS_OPERATION_HELPER,
            CONFLICT_ALGORITHM_VARIABLE)
        .build();
  }

  public static CodeBlock bindValuesVariable(@NonNull TableElement tableElement) {
    return CodeBlock.builder()
        .addStatement("final $T values = new $T($L)",
            BIND_VALUES_MAP,
            BIND_VALUES_MAP,
            optimalArrayMapSize(tableElement.getAllColumnsCount()))
        .build();
  }

  public static CodeBlock dbVariableFromPresentConnectionVariable() {
    return CodeBlock.builder()
        .addStatement("final $T db = $L.getWritableDatabase()",
            SUPPORT_SQLITE_DATABASE,
            DB_CONNECTION_VARIABLE)
        .build();
  }

  public static CodeBlock insertStatementVariableFromOpHelper(@NonNull TableElement tableElement,
                                                              @NonNull String variableName) {
    return CodeBlock.builder()
        .addStatement("final $T $L = $L.$L($S, $L, $L)",
            SUPPORT_SQLITE_STATEMENT,
            variableName,
            OPERATION_HELPER_VARIABLE,
            METHOD_GET_INSERT_STATEMENT,
            tableElement.getTableName(),
            FIELD_INSERT_SQL,
            MANAGER_VARIABLE)
        .build();
  }

  public static CodeBlock updateStatementVariableFromOpHelper(@NonNull TableElement tableElement,
                                                              @NonNull String variableName) {
    return CodeBlock.builder()
        .addStatement("final $T $L = $L.$L($S, $L, $L)",
            SUPPORT_SQLITE_STATEMENT,
            variableName,
            OPERATION_HELPER_VARIABLE,
            METHOD_GET_UPDATE_STATEMENT,
            tableElement.getTableName(),
            FIELD_UPDATE_SQL,
            MANAGER_VARIABLE)
        .build();
  }

  public static ParameterSpec entityParameter(@NonNull TypeName entityType) {
    return notNullParameter(entityType, ENTITY_VARIABLE);
  }

  public static ParameterSpec entityDbManagerParameter() {
    return notNullParameter(ENTITY_DB_MANAGER, MANAGER_VARIABLE);
  }

  public static ParameterSpec operationHelperParameter() {
    return notNullParameter(OPERATION_HELPER, OPERATION_HELPER_VARIABLE);
  }

  public static ParameterSpec connectionImplParameter() {
    return notNullParameter(DB_CONNECTION_IMPL, DB_CONNECTION_VARIABLE);
  }

  public static ParameterSpec operationByColumnsParameter() {
    return notNullParameter(OPERATION_BY_COLUMNS, OPERATION_BY_COLUMNS_VARIABLE);
  }

  public static String codeBlockEnd() {
    return ";\n";
  }

  public static void addOperationByColumnToOperationBuilder(TypeSpec.Builder builder,
                                                            TypeName interfaceName) {
    final TypeVariableName inputColumnType = TypeVariableName.get("C", ParameterizedTypeName.get(UNIQUE, NOT_NULLABLE_COLUMN));

    builder
        .addField(FieldSpec
            .builder(OPERATION_BY_COLUMNS, OPERATION_BY_COLUMNS_VARIABLE, PRIVATE, FINAL)
            .addAnnotation(NON_NULL)
            .initializer("new $T<>(2)", ARRAY_LIST)
            .build())
        .addMethod(MethodSpec
            .methodBuilder(METHOD_BY_COLUMN)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addAnnotation(NON_NULL)
            .addAnnotation(CHECK_RESULT)
            .addTypeVariable(inputColumnType)
            .addParameter(ParameterSpec.builder(inputColumnType, "column").build())
            .returns(interfaceName)
            .addStatement("this.$L.add(($T) column)",
                OPERATION_BY_COLUMNS_VARIABLE, COLUMN)
            .addStatement("return this")
            .build());
  }

  public static void addConflictAlgorithmToOperationBuilder(TypeSpec.Builder builder,
                                                            TypeName interfaceName) {
    builder.addField(FieldSpec.builder(TypeName.INT, CONFLICT_ALGORITHM_VARIABLE, PRIVATE)
        .initializer("SQLiteDatabase.CONFLICT_NONE")
        .build())
        .addMethod(setConflictAlgorithm(interfaceName));
  }

  public static MethodSpec setConflictAlgorithm(TypeName interfaceName) {
    return MethodSpec.methodBuilder(METHOD_SET_CONFLICT_ALGORITHM)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .addAnnotation(NON_NULL)
        .addAnnotation(CHECK_RESULT)
        .addParameter(ParameterSpec.builder(TypeName.INT, CONFLICT_ALGORITHM_VARIABLE)
            .addAnnotation(ConflictAlgorithm.class)
            .build())
        .returns(interfaceName)
        .addStatement("this.$N = $N", CONFLICT_ALGORITHM_VARIABLE, CONFLICT_ALGORITHM_VARIABLE)
        .addStatement("return this")
        .build();
  }

  public static void addDebugLogging(CodeBlock.Builder builder, String message) {
    if (BaseProcessor.GENERATE_LOGGING) {
      builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug($S)", SQLITE_MAGIC, LOG_UTIL, message);
    }
  }

  public static void addErrorLogging(MethodSpec.Builder methodBuilder, String message) {
    if (BaseProcessor.GENERATE_LOGGING) {
      methodBuilder.addStatement("if ($T.LOGGING_ENABLED) $T.logError($S)", SQLITE_MAGIC, LOG_UTIL, message);
    }
  }

  public static String getDefaultValue(@NonNull TypeName typeName) {
    if (!typeName.isPrimitive()) {
      return "null";
    }
    return PRIMITIVES_DEFAULT_VALUE_MAP.get(typeName);
  }

  public static void writeSource(Filer filer, TypeSpec classTypeSpec) throws IOException {
    writeSource(filer, classTypeSpec, PACKAGE_ROOT);
  }

  public static void writeSource(Filer filer, TypeSpec classTypeSpec, String packageName) throws IOException {
    JavaFile.builder(packageName, classTypeSpec)
        .addFileComment(Const.GENERATION_COMMENT)
        .build()
        .writeTo(filer);
  }

  public static int optimalArrayMapSize(int tableElementGraphNodeCount) {
    // 8 so that we could hit cached base array
    if (tableElementGraphNodeCount > SimpleArrayMap.BASE_SIZE * 2) {
      return tableElementGraphNodeCount;
    } else if (tableElementGraphNodeCount > SimpleArrayMap.BASE_SIZE) {
      return SimpleArrayMap.BASE_SIZE * 2;
    } else {
      return SimpleArrayMap.BASE_SIZE;
    }
  }

  public static String nameWithoutJavaBeansPrefix(ExecutableElement element) {
    final String rawName = element.getSimpleName().toString();
    return nameWithoutJavaBeansPrefix(rawName);
  }

  public static String nameWithoutJavaBeansPrefix(String rawName) {
    if (rawName.startsWith("get") && !rawName.equals("get")) {
      return rawName.substring(3);
    } else if (rawName.startsWith("is") && !rawName.equals("is")) {
      return rawName.substring(2);
    } else if (rawName.startsWith("set") && !rawName.equals("set")) {
      return rawName.substring(3);
    }
    return rawName;
  }

  public static boolean hasNullableAnnotation(Element columnElement) {
    for (AnnotationMirror annotationMirror : columnElement.getAnnotationMirrors()) {
      final String name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
      if ("Nullable".equals(name)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasNotNullAnnotation(Element columnElement) {
    for (AnnotationMirror annotationMirror : columnElement.getAnnotationMirrors()) {
      final String name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
      if ("NonNull".equals(name) || "NotNull".equals(name)) {
        return true;
      }
    }
    return false;
  }
}
