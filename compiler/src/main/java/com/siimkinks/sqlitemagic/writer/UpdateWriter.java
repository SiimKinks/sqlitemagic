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
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.STATEMENT_METHOD_MAP;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_UNSUBSCRIBED_UNEXPECTEDLY;
import static com.siimkinks.sqlitemagic.GlobalConst.FAILED_TO_UPDATE_ERR_MSG;
import static com.siimkinks.sqlitemagic.SqliteMagicProcessor.GENERATE_LOGGING;
import static com.siimkinks.sqlitemagic.WriterUtil.CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_UPDATE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_UPDATE_BUILDER;
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
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.conflictAlgorithmParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionImplParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbManagerParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.entityDbVariablesForOperationBuilder;
import static com.siimkinks.sqlitemagic.WriterUtil.entityParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.ifSubscriberUnsubscribed;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedIterable;
import static com.siimkinks.sqlitemagic.WriterUtil.updateStatementVariable;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_CONTENT_VALUES;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CALL_INTERNAL_UPDATE_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_UPDATE_INTERNAL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_UPDATE_WITH_CONFLICT_ALGORITHM_INTERNAL;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnFromProvidedIdsBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addBindColumnToStatementBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addIdNullCheck;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addImmutableIdsParameterIfNeeded;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addRxSingleTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addThrowOperationFailedExceptionWithEntityVariable;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionEndBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.addTransactionStartBlock;
import static com.siimkinks.sqlitemagic.writer.ModelPersistingGenerator.contentValuesAndDbVariables;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.CONFLICT_ALGORITHM_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.TRANSACTION_VARIABLE;

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
		addUpdateWithConflictAlgorithmInternalCallOnComplexColumnsIfNeeded(classBuilder);
	}

	@Override
	public void writeHandler(TypeSpec.Builder classBuilder) {
		final MethodSpec internalUpdateWithConflictAlgorithm = updateWithConflictAlgorithmInternal();
		final MethodSpec internalUpdate = updateInternal();
		classBuilder.addMethod(internalUpdateWithConflictAlgorithm)
				.addMethod(internalUpdate)
				.addType(update(internalUpdate, internalUpdateWithConflictAlgorithm))
				.addType(bulkUpdate());
	}

	// -------------------------------------------
	//                  DAO methods
	// -------------------------------------------

	private MethodSpec bindToUpdateStatement() {
		final boolean idColumnNullable = isIdColumnNullable();
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_UPDATE_STATEMENT)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(SQLITE_STATEMENT, "statement")
				.addParameter(tableElementTypeName, ENTITY_VARIABLE)
				.addStatement("statement.clearBindings()");
		if (idColumnNullable) {
			builder.addParameter(TypeName.LONG.box(), "id");
		}
		int colPos = 1;
		for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
			addBindColumnToStatementBlock(builder, colPos, columnElement);
			colPos++;
		}
		if (idColumnNullable) {
			addBindIdColumnToStatementBlock(builder, colPos);
		} else {
			addBindColumnToStatementBlock(builder, colPos, tableElement.getIdColumn());
		}
		return builder.build();
	}

	private MethodSpec bindToUpdateStatementWithComplexColumns() {
		final boolean idColumnNullable = isIdColumnNullable();
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(SQLITE_STATEMENT, "statement")
				.addParameter(tableElementTypeName, ENTITY_VARIABLE)
				.addStatement("statement.clearBindings()");
		if (idColumnNullable) {
			builder.addParameter(TypeName.LONG.box(), "id");
		}
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
		if (idColumnNullable) {
			addBindIdColumnToStatementBlock(builder, colPos);
		} else {
			addBindColumnToStatementBlock(builder, colPos, tableElement.getIdColumn());
		}
		return builder.build();
	}

	private void addBindIdColumnToStatementBlock(MethodSpec.Builder builder, int colPos) {
		final ColumnElement idColumn = tableElement.getIdColumn();
		final String bindMethod = STATEMENT_METHOD_MAP.get(idColumn.getSerializedType().getQualifiedName());
		builder.addStatement("statement.$L($L, id)", bindMethod, colPos);
	}

	private void addUpdateWithConflictAlgorithmInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder) {
		addUpdateMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment,
				METHOD_CALL_INTERNAL_UPDATE_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS,
				METHOD_UPDATE_WITH_CONFLICT_ALGORITHM_INTERNAL,
				new ReturnCallback2<String, ParameterSpec, ColumnElement>() {
					@Override
					public String call(ParameterSpec param, ColumnElement o2) {
						return param.name;
					}
				},
				ParameterSpec.builder(CONTENT_VALUES, "values").build(),
				ParameterSpec.builder(SQLITE_DATABASE, "db").build(),
				conflictAlgorithmParameter());
	}

	private void addUpdateMethodInternalCallOnComplexColumnsIfNeeded(TypeSpec.Builder daoClassBuilder) {
		addUpdateMethodInternalCallOnComplexColumnsIfNeeded(daoClassBuilder, entityEnvironment,
				METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS,
				METHOD_UPDATE_INTERNAL,
				COMPLEX_COLUMN_PARAM_TO_ENTITY_DB_MANAGER,
				connectionImplParameter());
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

	private TypeSpec update(MethodSpec update, MethodSpec updateWithConflictAlgorithm) {
		final MethodSpec updateExecute = updateExecute(update, updateWithConflictAlgorithm);
		final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_UPDATE, ENTITY_UPDATE_BUILDER, tableElementTypeName, ENTITY_VARIABLE);
		addConflictAlgorithmToOperationBuilder(builder, ENTITY_UPDATE_BUILDER);
		return builder.addSuperinterface(ENTITY_UPDATE_BUILDER)
				.addMethod(updateExecute)
				.addMethod(updateObserve(builder, updateExecute))
				.build();
	}

	private MethodSpec updateExecute(MethodSpec update, MethodSpec updateWithConflictAlgorithm) {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(TypeName.BOOLEAN)
				.addCode(entityDbVariablesForOperationBuilder(tableElement));
		final boolean hasComplexColumns = tableElement.hasAnyPersistedComplexColumns();
		if (hasComplexColumns) {
			addTransactionStartBlock(builder);
		}

		builder.beginControlFlow("if ($N == SQLiteDatabase.CONFLICT_NONE || $N == SQLiteDatabase.CONFLICT_ABORT)",
				CONFLICT_ALGORITHM_VARIABLE, CONFLICT_ALGORITHM_VARIABLE);
		FormatData internalMethodCall = FormatData.create("$N($L, $L)", update, ENTITY_VARIABLE, MANAGER_VARIABLE);
		if (hasComplexColumns) {
			addCallToInternalUpdateWithTransactionHandling(builder, internalMethodCall);
		} else {
			addCallToInternalUpdate(builder, allTableTriggers, internalMethodCall);
		}

		builder.endControlFlow();

		internalMethodCall = FormatData.create("$N($L, values, db, $L)", updateWithConflictAlgorithm, ENTITY_VARIABLE, CONFLICT_ALGORITHM_VARIABLE);
		builder.addCode(contentValuesAndDbVariables());
		if (hasComplexColumns) {
			addCallToInternalUpdateWithTransactionHandling(builder, internalMethodCall);
		} else {
			addCallToInternalUpdate(builder, allTableTriggers, internalMethodCall);
		}

		if (hasComplexColumns) {
			addUpdateTransactionEndBlock(builder, allTableTriggers);
		}

		return builder.build();
	}

	private MethodSpec updateObserve(TypeSpec.Builder typeBuilder, final MethodSpec updateExecute) {
		final TypeName entityTypeName = TypeName.BOOLEAN.box();
		final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
				.addAnnotation(Override.class);
		addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
			@Override
			public void call(MethodSpec.Builder builder) {
				builder.beginControlFlow("if (!$N())", updateExecute)
						.addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, FAILED_TO_UPDATE_ERR_MSG)
						.endControlFlow()
						.addStatement("return $T.TRUE", TypeName.BOOLEAN.box());
			}
		});
		addRxSingleCreateFromCallableParentClass(builder);
		return builder.build();
	}

	private MethodSpec updateInternal() {
		final boolean hasAnyPersistedComplexColumns = tableElement.hasAnyPersistedComplexColumns();
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_UPDATE_INTERNAL)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(entityParameter(tableElementTypeName))
				.addParameter(entityDbManagerParameter())
				.returns(TypeName.BOOLEAN);
		addIdColumnNullCheckIfNeeded(builder);
		addUpdateLoggingStatement(builder);

		if (hasAnyPersistedComplexColumns && GENERATE_LOGGING) {
			builder.addStatement("final int rowsAffected");
		}

		builder.addCode(updateStatementVariable())
				.beginControlFlow("synchronized (stm)")
				.addStatement("$T.$L(stm, $L$L)", daoClassName, METHOD_BIND_TO_UPDATE_STATEMENT, ENTITY_VARIABLE,
						isIdColumnNullable() ? ", id" : "");

		if (!hasAnyPersistedComplexColumns) {
			if (!GENERATE_LOGGING) {
				builder.addStatement("return stm.executeUpdateDelete() > 0");
			} else {
				builder.addStatement("final int rowsAffected = stm.executeUpdateDelete()");
				addAfterUpdateLoggingStatement(builder);
				builder.addStatement("return rowsAffected > 0");
			}
		} else {
			if (!GENERATE_LOGGING) {
				builder.beginControlFlow("if (stm.executeUpdateDelete() > 0)")
						.addStatement("return $T.$L($L, $L.getDbConnection())", daoClassName, METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS, ENTITY_VARIABLE, MANAGER_VARIABLE)
						.endControlFlow()
						.addStatement("return false");
			} else {
				builder.addStatement("rowsAffected = stm.executeUpdateDelete()");
				addAfterUpdateLoggingStatement(builder);
				builder.endControlFlow()
						.beginControlFlow("if (rowsAffected > 0)")
						.addStatement("return $T.$L($L, $L.getDbConnection())", daoClassName, METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS, ENTITY_VARIABLE, MANAGER_VARIABLE)
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

	private MethodSpec updateWithConflictAlgorithmInternal() {
		final boolean hasAnyComplexColumns = tableElement.hasAnyPersistedComplexColumns();
		final boolean idColumnNullable = isIdColumnNullable();
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_UPDATE_WITH_CONFLICT_ALGORITHM_INTERNAL)
				.addModifiers(STATIC_METHOD_MODIFIERS)
				.addParameter(tableElementTypeName, ENTITY_VARIABLE)
				.addParameter(CONTENT_VALUES, "values")
				.addParameter(SQLITE_DATABASE, "db")
				.addParameter(conflictAlgorithmParameter())
				.returns(TypeName.BOOLEAN);
		if (idColumnNullable) {
			builder.addCode(entityEnvironment.getFinalIdVariable());
			addIdNullCheck(builder, "Can't execute updateWithConflictAlgorithm - id column null");
		}
		addUpdateLoggingStatement(builder);
		builder.addStatement("$T.$L($L, values)", daoClassName, METHOD_BIND_TO_CONTENT_VALUES, ENTITY_VARIABLE);
		final FormatData whereIdStatementPart = idColumnNullable ? entityEnvironment.getWhereIdStatementPartWithProvidedIdVariable("id") : entityEnvironment.getWhereIdStatementPart();
		final FormatData updateExecutePart = FormatData.create(
				String.format("db.updateWithOnConflict($S, values, %s, conflictAlgorithm)", whereIdStatementPart.getFormat()),
				whereIdStatementPart.getWithOtherArgsBefore(tableElement.getTableName()));
		if (!hasAnyComplexColumns) {
			if (!GENERATE_LOGGING) {
				builder.addStatement(String.format("return %s > 0", updateExecutePart.getFormat()), updateExecutePart.getArgs());
			} else {
				builder.addStatement(String.format("final boolean updateSuccessful = %s > 0", updateExecutePart.getFormat()), updateExecutePart.getArgs());
				addAfterUpdateBooleanLoggingStatement(builder);
				builder.addStatement("return updateSuccessful");
			}
		} else {
			if (!GENERATE_LOGGING) {
				builder.beginControlFlow(String.format("if (%s > 0)", updateExecutePart.getFormat()), updateExecutePart.getArgs());
			} else {
				builder.addStatement(String.format("final boolean updateSuccessful = %s > 0", updateExecutePart.getFormat()), updateExecutePart.getArgs());
				addAfterUpdateBooleanLoggingStatement(builder);
				builder.beginControlFlow("if (updateSuccessful)");
			}
			builder.addStatement("return $T.$L($L, values, db, conflictAlgorithm)", daoClassName, METHOD_CALL_INTERNAL_UPDATE_WITH_CONFLICT_ALGORITHM_ON_COMPLEX_COLUMNS, ENTITY_VARIABLE)
					.endControlFlow()
					.addStatement("return false");
		}
		return builder.build();
	}

	private void addCallToInternalUpdateWithTransactionHandling(MethodSpec.Builder builder,
	                                                            FormatData internalMethodCall) {
		builder.beginControlFlow(String.format("if (%s)", internalMethodCall.getFormat()), internalMethodCall.getArgs())
				.addStatement("$L.markSuccessful()", TRANSACTION_VARIABLE)
				.addStatement("success = true")
				.addStatement("return true")
				.endControlFlow()
				.addStatement("return false");
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
				.endControlFlow();
	}

	private TypeSpec bulkUpdate() {
		final TypeName observeType = TypeName.BOOLEAN.box();
		final ParameterizedTypeName interfaceType = ParameterizedTypeName.get(ENTITY_BULK_UPDATE_BUILDER, observeType);
		final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_UPDATE, interfaceType, iterable, OBJECTS_VARIABLE);
		return builder
				.addSuperinterface(interfaceType)
				.addMethod(bulkUpdateExecute())
				.addMethod(bulkUpdateObserve(builder, observeType))
				.build();
	}

	private MethodSpec bulkUpdateObserve(TypeSpec.Builder typeBuilder, TypeName observeType) {
		final MethodSpec.Builder builder = operationRxSingleMethod(observeType)
				.addAnnotation(Override.class);
		addRxSingleCreateFromParentClass(builder);
		addRxSingleOnSubscribeToType(typeBuilder, observeType, new Callback<MethodSpec.Builder>() {
			@Override
			public void call(MethodSpec.Builder builder) {
				builder.addCode(entityDbVariablesForOperationBuilder(tableElement));
				addTransactionStartBlock(builder);
				addBulkUpdateTopBlock(builder);
				builder.nextControlFlow("else $L", ifSubscriberUnsubscribed())
						.addStatement("throw new $T($S)", OPERATION_FAILED_EXCEPTION, ERROR_UNSUBSCRIBED_UNEXPECTEDLY)
						.endControlFlow()
						.endControlFlow()
						.endControlFlow();
				addRxSingleTransactionEndBlock(builder, allTableTriggers, "Boolean.TRUE");
			}
		});
		return builder.build();
	}

	private MethodSpec bulkUpdateExecute() {
		final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(TypeName.BOOLEAN)
				.addCode(entityDbVariablesForOperationBuilder(tableElement));
		addTransactionStartBlock(builder);
		addBulkUpdateTopBlock(builder);
		builder.endControlFlow()
				.endControlFlow()
				.endControlFlow();
		addTransactionEndBlock(builder, allTableTriggers, "return true", "return false");
		return builder.build();
	}

	private void addBulkUpdateTopBlock(MethodSpec.Builder builder) {
		final boolean idColumnNullable = isIdColumnNullable();
		builder.addCode(updateStatementVariable())
				.beginControlFlow("synchronized (stm)")
				.beginControlFlow("for ($T $L : $L)", tableElementTypeName, ENTITY_VARIABLE, OBJECTS_VARIABLE);
		addUpdateLoggingStatement(builder);
		if (idColumnNullable) {
			builder.addCode(entityEnvironment.getFinalIdVariable());
			addIdNullCheck(builder, "Can't execute update - id column null");
		}
		builder.addStatement("$T.$L(stm, $L$L)", daoClassName, METHOD_BIND_TO_UPDATE_STATEMENT, ENTITY_VARIABLE,
				idColumnNullable ? ", id" : "");
		if (tableElement.hasAnyPersistedComplexColumns()) {
			builder.beginControlFlow("if (stm.executeUpdateDelete() <= 0 || !$T.$L($L, $L.getDbConnection()))", daoClassName, METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS, ENTITY_VARIABLE, MANAGER_VARIABLE);
		} else {
			builder.beginControlFlow("if (stm.executeUpdateDelete() <= 0)");
		}
		addThrowOperationFailedExceptionWithEntityVariable(builder, "Failed to update");
	}

	private void addUpdateLoggingStatement(MethodSpec.Builder builder) {
		if (GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"UPDATE\\n  table: $L\\n  object: %s\", $L.toString())",
					SQLITE_MAGIC, LOG_UTIL, tableElement.getTableName(), ENTITY_VARIABLE);
		}
	}

	private void addAfterUpdateBooleanLoggingStatement(MethodSpec.Builder builder) {
		if (GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"UPDATE successful: %s\", updateSuccessful)", SQLITE_MAGIC, LOG_UTIL);
		}
	}

	private void addAfterUpdateLoggingStatement(MethodSpec.Builder builder) {
		if (GENERATE_LOGGING) {
			builder.addStatement("if ($T.LOGGING_ENABLED) $T.logDebug(\"UPDATE rows affected: %s\", rowsAffected)", SQLITE_MAGIC, LOG_UTIL);
		}
	}
}
