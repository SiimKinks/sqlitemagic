package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

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
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.MutableInt;
import com.siimkinks.sqlitemagic.util.StringUtil;
import com.siimkinks.sqlitemagic.writer.EntityEnvironment;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

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
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CONNECTION_PROVIDER;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CREATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_GET_INSERT_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_GET_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_OBSERVE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_CONFLICT_ALGORITHM;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.tableNameFromStructureConstant;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxObservableTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.CONFLICT_ALGORITHM_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.STATEMENT_VARIABLE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * @author Siim Kinks
 */
public class WriterUtil {

	public static final ClassName SQLITE_DATABASE = ClassName.get("android.database.sqlite", "SQLiteDatabase");
	public static final ClassName CONTENT_VALUES = ClassName.get("android.content", "ContentValues");
	public static final ClassName CURSOR = ClassName.get("android.database", "Cursor");
	public static final ClassName SQLITE_STATEMENT = ClassName.get("android.database.sqlite", "SQLiteStatement");
	public static final ClassName SQL_EXCEPTION = ClassName.get("android.database", "SQLException");
	public static final ClassName CHECK_RESULT = ClassName.get("android.support.annotation", "CheckResult");
	public static final ClassName NON_NULL = ClassName.get("android.support.annotation", "NonNull");
	public static final ClassName NULLABLE = ClassName.get("android.support.annotation", "Nullable");

	public static final TypeName ATOMIC_SQLITE_STATEMENT = ParameterizedTypeName.get(ClassName.get(AtomicReference.class), SQLITE_STATEMENT);

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
	public static final ClassName SQLITE_MAGIC = ClassName.get(SqliteMagic.class);
	public static final ClassName DB_CONNECTION = ClassName.get(DbConnection.class);
	public static final ClassName DB_CONNECTION_IMPL = ClassName.get(DbConnectionImpl.class);
	public static final ClassName OPERATION_FAILED_EXCEPTION = ClassName.get(OperationFailedException.class);
	public static final ClassName TRANSACTION = ClassName.get(Transaction.class);
	public static final ClassName MUTABLE_INT = ClassName.get(MutableInt.class);
	public static final ClassName FROM = ClassName.get(Select.From.class);
	public static final ClassName TABLE = ClassName.get(Table.class);
	public static final ClassName COLUMN = ClassName.get(Column.class);
	public static final ClassName NUMERIC_COLUMN = ClassName.get(NumericColumn.class);
	public static final ClassName COMPLEX_COLUMN = ClassName.get(ComplexColumn.class);
	public static final ClassName JOIN_CLAUSE = ClassName.get(JoinClause.class);
	public static final ClassName FAST_CURSOR = ClassName.get("com.siimkinks.sqlitemagic", "FastCursor");
	public static final ClassName SIMPLE_ARRAY_MAP = ClassName.get(SimpleArrayMap.class);
	public static final ClassName STRING_ARRAY_SET = ClassName.get(StringArraySet.class);
	public static final ClassName COMPILED_N_COLUMNS_SELECT_IMPL = ClassName.get(CompiledSelectImpl.class);
	public static final ClassName COMPILED_N_COLUMNS_SELECT = ClassName.get(CompiledSelect.class);
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
	public static final ParameterizedTypeName SYSTEM_RENAMED_TABLES_TYPE_NAME =
			ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, ClassName.get(String.class), ParameterizedTypeName.get(LinkedList.class, String.class));

	public static final ClassName OBSERVABLE = ClassName.get("rx", "Observable");
	public static final ClassName OBSERVABLE_ON_SUBSCRIBE = ClassName.get("rx", "Observable", "OnSubscribe");
	public static final ClassName OBSERVABLE_SUBSCRIBER = ClassName.get("rx", "Subscriber");
	public static final ClassName SINGLE = ClassName.get("rx", "Single");
	public static final ClassName SINGLE_ON_SUBSCRIBE = ClassName.get("rx", "Single", "OnSubscribe");
	public static final ClassName SINGLE_SUBSCRIBER = ClassName.get("rx", "SingleSubscriber");
	public static final ClassName SCHEDULERS = ClassName.get("rx.schedulers", "Schedulers");

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

	public static MethodSpec buildSqlTransactionMethod(String methodName, CodeBlock sqlTransactionBody) {
		return buildSqlTransactionMethod(MethodSpec.methodBuilder(methodName), sqlTransactionBody);
	}

	public static void addSingleton(TypeSpec.Builder classBuilder, ClassName className) {
		MethodSpec noArgsConstructor = MethodSpec.constructorBuilder()
				.addModifiers(PRIVATE)
				.build();
		classBuilder.addMethod(noArgsConstructor);
		TypeSpec singletonHolder = TypeSpec.classBuilder("SingletonHolder")
				.addModifiers(PRIVATE, STATIC)
				.addField(FieldSpec.builder(className, "instance", PUBLIC, STATIC, FINAL)
						.initializer("new $T()", className)
						.build())
				.build();
		classBuilder.addType(singletonHolder);
		ClassName singletonHolderClass = ClassName.bestGuess("SingletonHolder");
		MethodSpec instanceGetter = MethodSpec.methodBuilder("getInstance")
				.addModifiers(Const.STATIC_METHOD_MODIFIERS)
				.addStatement("return $T.instance", singletonHolderClass)
				.returns(className)
				.build();
		classBuilder.addMethod(instanceGetter);
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

	public static MethodSpec buildSqlTransactionMethod(MethodSpec.Builder methodBuilder, CodeBlock sqlTransactionBody) {
		return methodBuilder
				.addModifiers(Const.STATIC_METHOD_MODIFIERS)
				.addParameter(SQLITE_DATABASE, "db")
				.addStatement("db.beginTransaction()")
				.beginControlFlow("try")
				.addCode(sqlTransactionBody)
				.addStatement("db.setTransactionSuccessful()")
				.nextControlFlow("catch (Exception e)")
				.addStatement("$L $T.logError(e, \"Error while executing db transaction\")", IF_LOGGING_ENABLED, LOG_UTIL)
				.nextControlFlow("finally")
				.addStatement("db.endTransaction()")
				.endControlFlow()
				.build();
	}

	public static MethodSpec.Builder operationRxObserveMethod(TypeName entityTypeName) {
		return operationRxObserveMethod(METHOD_OBSERVE, entityTypeName);
	}

	public static MethodSpec.Builder operationRxObserveMethod(String methodName, TypeName entityTypeName) {
		return MethodSpec.methodBuilder(methodName)
				.addModifiers(Modifier.PUBLIC)
				.addAnnotation(NON_NULL)
				.addAnnotation(CHECK_RESULT)
				.returns(ParameterizedTypeName.get(OBSERVABLE, entityTypeName));
	}

	public static MethodSpec.Builder privateOperationRxObserveMethod(String methodName, TypeName entityTypeName) {
		return MethodSpec.methodBuilder(methodName)
				.addModifiers(PRIVATE)
				.returns(ParameterizedTypeName.get(OBSERVABLE, entityTypeName));
	}

	public static CodeBlock.Builder wrapIntoRxObservableCreate(TypeName observedType, Callback<MethodSpec.Builder> methodBuildCallback) {
		final MethodSpec.Builder callbackMethodBuilder = MethodSpec.methodBuilder("call")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(ParameterizedTypeName.get(OBSERVABLE_SUBSCRIBER, WildcardTypeName.supertypeOf(observedType)), "subscriber");
		methodBuildCallback.call(callbackMethodBuilder);
		TypeSpec onSubscribe = TypeSpec.anonymousClassBuilder("")
				.addSuperinterface(ParameterizedTypeName.get(OBSERVABLE_ON_SUBSCRIBE, observedType))
				.addMethod(callbackMethodBuilder.build())
				.build();
		return CodeBlock.builder()
				.add("$T.create($L)", OBSERVABLE, onSubscribe);
	}

	public static void addWrappedReturnedRxObservableCreate(MethodSpec.Builder builder, TypeName observedType, final Callback<MethodSpec.Builder> methodBuildCallback) {
		builder.addCode("return ")
				.addCode(wrapIntoRxObservableCreate(observedType, methodBuildCallback)
						.add(chainedScheduler())
						.build());
	}

	public static void addWrappedReturnedTransactionHandledRxObservableCreate(MethodSpec.Builder builder,
	                                                                          TypeName observedType,
	                                                                          final TableElement tableElement,
	                                                                          final Set<TableElement> allTableTriggers,
	                                                                          final Callback<MethodSpec.Builder> methodBuildCallback) {
		builder.addCode("return ")
				.addCode(wrapIntoRxObservableCreate(observedType, new Callback<MethodSpec.Builder>() {
					@Override
					public void call(MethodSpec.Builder builder) {
						builder.addCode(entityDbVariablesForOperationBuilder(tableElement));
						addTransactionStartBlock(builder);
						methodBuildCallback.call(builder);
						addRxObservableTransactionEndBlock(builder, allTableTriggers);
					}
				}).add(chainedScheduler()).build());
	}

	public static CodeBlock.Builder wrapIntoErrorHandledRxObservableCreate(TypeName observedType, final Callback<MethodSpec.Builder> methodBuildCallback) {
		return wrapIntoRxObservableCreate(observedType, new Callback<MethodSpec.Builder>() {
			@Override
			public void call(MethodSpec.Builder builder) {
				builder.beginControlFlow("try");
				methodBuildCallback.call(builder);
				builder.nextControlFlow("catch (Throwable e)")
						.beginControlFlow(ifNotSubscriberUnsubscribed())
						.addStatement(subscriberOnError())
						.endControlFlow()
						.endControlFlow();
			}
		});
	}

	public static void addWrappedReturnedErrorHandledRxObservableCreate(MethodSpec.Builder builder, TypeName observedType, final Callback<MethodSpec.Builder> methodBuildCallback) {
		builder.addCode("return ")
				.addCode(wrapIntoErrorHandledRxObservableCreate(observedType, methodBuildCallback)
						.add(chainedScheduler())
						.build());
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

	public static CodeBlock.Builder wrapIntoRxSingleCreate(TypeName observedType, Callback<MethodSpec.Builder> methodBuildCallback) {
		final MethodSpec.Builder callbackMethodBuilder = MethodSpec.methodBuilder("call")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addException(Exception.class)
				.returns(observedType);
		methodBuildCallback.call(callbackMethodBuilder);
		final TypeSpec callable = TypeSpec.anonymousClassBuilder("")
				.addSuperinterface(ParameterizedTypeName.get(CALLABLE, observedType))
				.addMethod(callbackMethodBuilder.build())
				.build();
		return CodeBlock.builder()
				.add("$T.fromCallable($L)", SINGLE, callable);
	}

	public static void addWrappedReturnedRxSingleCreate(MethodSpec.Builder builder, TypeName observedType, final Callback<MethodSpec.Builder> methodBuildCallback) {
		builder.addCode("return ")
				.addCode(wrapIntoRxSingleCreate(observedType, methodBuildCallback)
						.add(chainedScheduler())
						.build());
	}

	public static void addRxSingleCreateFromCallableParentClass(MethodSpec.Builder builder) {
		builder.addCode("return $T.fromCallable(this)", SINGLE)
				.addCode(chainedScheduler());
	}

	public static void addRxSingleCreateFromParentClass(MethodSpec.Builder builder) {
		builder.addCode("return $T.create(this)", SINGLE)
				.addCode(chainedScheduler());
	}

	public static void addRxObservableCreateFromParentClass(MethodSpec.Builder builder) {
		builder.addCode("return $T.create(this)", OBSERVABLE)
				.addCode(chainedScheduler());
	}

	public static CodeBlock chainedScheduler() {
		return CodeBlock.builder()
				.add(";\n")
				.build();
	}

	public static String ifSubscriberUnsubscribed() {
		return "if (subscriber.isUnsubscribed())";
	}

	public static String ifNotSubscriberUnsubscribed() {
		return "if (!subscriber.isUnsubscribed())";
	}

	public static String subscriberOnNext(String variableName) {
		return String.format("subscriber.onNext(%s)", variableName);
	}

	public static String subscriberOnSuccess(String variableName) {
		return String.format("subscriber.onSuccess(%s)", variableName);
	}

	public static String subscriberOnCompleted() {
		return "subscriber.onCompleted()";
	}

	public static String subscriberOnError() {
		return "subscriber.onError(e)";
	}

	public static TypeSpec.Builder addRxObservableOnSubscribeToType(@NonNull TypeSpec.Builder typeBuilder,
	                                                                @NonNull TypeName observedType,
	                                                                @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
		return addRxOnSubscribeToType(typeBuilder, observedType,
				OBSERVABLE_SUBSCRIBER, OBSERVABLE_ON_SUBSCRIBE,
				methodBodyBuildCallback);
	}

	public static TypeSpec.Builder addRxSingleOnSubscribeToType(@NonNull TypeSpec.Builder typeBuilder,
	                                                            @NonNull TypeName observedType,
	                                                            @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
		return addRxOnSubscribeToType(typeBuilder, observedType,
				SINGLE_SUBSCRIBER, SINGLE_ON_SUBSCRIBE,
				methodBodyBuildCallback);
	}

	private static TypeSpec.Builder addRxOnSubscribeToType(@NonNull TypeSpec.Builder typeBuilder,
	                                                       @NonNull TypeName observedType,
	                                                       @NonNull ClassName subscriberClass,
	                                                       @NonNull ClassName onSubscribeClass,
	                                                       @NonNull Callback<MethodSpec.Builder> methodBodyBuildCallback) {
		final MethodSpec.Builder callBuilder = MethodSpec.methodBuilder("call")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(ParameterizedTypeName.get(subscriberClass, WildcardTypeName.supertypeOf(observedType)), "subscriber");
		methodBodyBuildCallback.call(callBuilder);

		typeBuilder.addSuperinterface(ParameterizedTypeName.get(onSubscribeClass, observedType))
				.addMethod(callBuilder.build());
		return typeBuilder;
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
				.addStatement("final $T $L = $L.getEntityDbManager($L)",
						ENTITY_DB_MANAGER,
						MANAGER_VARIABLE,
						DB_CONNECTION_VARIABLE,
						tableElement.getTablePos())
				.build();
	}

	public static CodeBlock entityDbVariablesForOperationBuilder(TableElement tableElement) {
		return CodeBlock.builder()
				.add(dbConnectionVariable())
				.add(entityDbManagerVariableFromDbConnection(tableElement))
				.build();
	}

	public static CodeBlock dbVariableFromPresentConnectionVariable() {
		return CodeBlock.builder()
				.addStatement("final $T db = $L.getWritableDatabase()",
						SQLITE_DATABASE,
						DB_CONNECTION_VARIABLE)
				.build();
	}

	public static CodeBlock insertStatementVariable() {
		return insertStatementVariable(STATEMENT_VARIABLE);
	}

	public static CodeBlock insertStatementVariable(@NonNull String variableName) {
		return CodeBlock.builder()
				.addStatement("final $T $L = $L.$L($L)",
						SQLITE_STATEMENT,
						variableName,
						MANAGER_VARIABLE,
						METHOD_GET_INSERT_STATEMENT,
						FIELD_INSERT_SQL)
				.build();
	}

	public static CodeBlock updateStatementVariable() {
		return updateStatementVariable(STATEMENT_VARIABLE);
	}

	public static CodeBlock updateStatementVariable(@NonNull String variableName) {
		return CodeBlock.builder()
				.addStatement("final $T $L = $L.$L($L)",
						SQLITE_STATEMENT,
						variableName,
						MANAGER_VARIABLE,
						METHOD_GET_UPDATE_STATEMENT,
						FIELD_UPDATE_SQL)
				.build();
	}

	public static ParameterSpec entityParameter(@NonNull TypeName entityType) {
		return notNullParameter(entityType, ENTITY_VARIABLE);
	}

	public static ParameterSpec entityDbManagerParameter() {
		return notNullParameter(ENTITY_DB_MANAGER, MANAGER_VARIABLE);
	}

	public static ParameterSpec connectionImplParameter() {
		return notNullParameter(DB_CONNECTION_IMPL, DB_CONNECTION_VARIABLE);
	}

	public static ParameterSpec conflictAlgorithmParameter() {
		return ParameterSpec.builder(TypeName.INT, "conflictAlgorithm")
				.addAnnotation(ConflictAlgorithm.class)
				.build();
	}

	public static String codeBlockEnd() {
		return ";\n";
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
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
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
		if (SqliteMagicProcessor.GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug($S)", SQLITE_MAGIC, LOG_UTIL, message);
		}
	}

	public static void addErrorLogging(MethodSpec.Builder methodBuilder, String message) {
		if (SqliteMagicProcessor.GENERATE_LOGGING) {
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
		JavaFile javaFile = JavaFile.builder(packageName, classTypeSpec)
				.addFileComment(Const.GENERATION_COMMENT)
				.indent("\t")
				.build();
		javaFile.writeTo(filer);
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
