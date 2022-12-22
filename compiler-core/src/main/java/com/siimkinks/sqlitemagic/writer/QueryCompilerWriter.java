package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.ElementGraphWalker;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.ConditionCallback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.FROM;
import static com.siimkinks.sqlitemagic.WriterUtil.JOIN_CLAUSE;
import static com.siimkinks.sqlitemagic.WriterUtil.SIMPLE_ARRAY_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.SQL_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.TABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.optimalArrayMapSize;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_DEEP_QUERY_PARTS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_DEEP_QUERY_PARTS_INTERNAL;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_SHALLOW_QUERY_PARTS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_SHALLOW_QUERY_PARTS_INTERNAL;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedTableStructureInterfaceName;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.tableStructureConstant;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.tableStructureIdConstant;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.fromSelectClauseParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.select1Param;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.selectFromTablesParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.tableGraphNodeNamesParam;
import static com.siimkinks.sqlitemagic.writer.ModelRetrievingGenerator.SYSTEM_RENAMED_TABLES_TYPE_NAME;
import static com.siimkinks.sqlitemagic.writer.ModelRetrievingGenerator.systemRenamedTablesParam;
import static com.siimkinks.sqlitemagic.writer.StructureWriter.columnFieldName;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class QueryCompilerWriter implements OperationWriter {

  private final EntityEnvironment entityEnvironment;
  private final TableElement tableElement;
  private final TypeName tableElementTypeName;
  private final FormatData tableStructureConstant;
  private final TypeName tableType;

  public static QueryCompilerWriter create(EntityEnvironment entityEnvironment) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    final TableElement tableElement = entityEnvironment.getTableElement();
    return builder()
        .entityEnvironment(entityEnvironment)
        .tableElement(tableElement)
        .tableElementTypeName(tableElementTypeName)
        .tableStructureConstant(tableStructureConstant(tableElement))
        .tableType(TABLE)
        .build();
  }

  @Override
  public void writeDao(TypeSpec.Builder classBuilder) {
  }

  @Override
  public void writeHandler(TypeSpec.Builder handlerClassBuilder) {
    if (tableElement.hasAnyPersistedComplexColumns()) {
      final MethodSpec deepQueryPartsAddInternal = deepQueryPartsAddInternal(true);
      final MethodSpec deepQueryPartsAddAllInternal = deepQueryPartsAddInternal(false);
      handlerClassBuilder
          .addMethod(queryPartsAdd(METHOD_ADD_DEEP_QUERY_PARTS, deepQueryPartsAddInternal, deepQueryPartsAddAllInternal))
          .addMethod(deepQueryPartsAddAllInternal)
          .addMethod(deepQueryPartsAddInternal);
      if (tableElement.isQueryPartNeededForShallowQuery()) {
        final MethodSpec shallowQueryPartsAddInternal = shallowQueryPartsAddInternal(true);
        final MethodSpec shallowQueryPartsAddAllInternal = shallowQueryPartsAddInternal(false);
        handlerClassBuilder
            .addMethod(queryPartsAdd(METHOD_ADD_SHALLOW_QUERY_PARTS, shallowQueryPartsAddInternal, shallowQueryPartsAddAllInternal))
            .addMethod(shallowQueryPartsAddAllInternal)
            .addMethod(shallowQueryPartsAddInternal);
      }
    }
  }

  private MethodSpec queryPartsAdd(String methodName, MethodSpec queryPartsAddInternal, MethodSpec queryPartsAddAllInternal) {
    final Integer tableElementGraphNodeCount = tableElement.getGraphNodeCount();
    final MethodSpec.Builder builder = queryPartsAddMethodSignature(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addStatement("$T systemRenamedTables = null", SYSTEM_RENAMED_TABLES_TYPE_NAME)
        .beginControlFlow("if (selectFromTables == null || selectFromTables.isEmpty())")
        .addStatement("$N(from.table, false, from.joins)", queryPartsAddAllInternal)
        .nextControlFlow("else")
        .addStatement("systemRenamedTables = new $T<>($L)",
            SIMPLE_ARRAY_MAP,
            optimalArrayMapSize(tableElementGraphNodeCount))
        .addStatement("$N(from.table, false, from.joins, selectFromTables, systemRenamedTables, tableGraphNodeNames, $S, select1)",
            queryPartsAddInternal, "")
        .beginControlFlow("if (systemRenamedTables.isEmpty())")
        .addStatement("systemRenamedTables = null")
        .endControlFlow()
        .endControlFlow()
        .addStatement("return systemRenamedTables");
    return builder.build();
  }

  @NonNull
  public static MethodSpec.Builder queryPartsAddMethodSignature(String methodName) {
    return MethodSpec.methodBuilder(methodName)
        .returns(SYSTEM_RENAMED_TABLES_TYPE_NAME)
        .addParameter(fromSelectClauseParam())
        .addParameter(selectFromTablesParam())
        .addParameter(tableGraphNodeNamesParam())
        .addParameter(select1Param());
  }

  private MethodSpec shallowQueryPartsAddInternal(boolean fromSelection) {
    final Set<String> renameNeedingTables = new HashSet<>();
    final Set<String> duplicateTables = new HashSet<>();
    final Set<String> lookedTables = new HashSet<>();
    final Set<String> allGraphTables = new HashSet<>();

    for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
      if (!columnElement.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = columnElement.getReferencedTable();
      final String referencedTableName = referencedTable.getTableName();
      if (lookedTables.contains(referencedTableName)) {
        duplicateTables.add(referencedTableName);
      }

      ElementGraphWalker.depthFirst(referencedTable,
          new ConditionCallback<TableElement>() {
            @Override
            public boolean call(TableElement tableElement) {
              return tableElement.isQueryPartNeededForShallowQuery();
            }
          },
          getTableRenamesFindingVisitCallback(renameNeedingTables, allGraphTables, referencedTableName));

      lookedTables.add(referencedTableName);
    }
    renameNeedingTables.addAll(duplicateTables);

    return queryPartsAddInternal(fromSelection, METHOD_ADD_SHALLOW_QUERY_PARTS_INTERNAL,
        renameNeedingTables,
        new ConditionCallback<ColumnElement>() {
          @Override
          public boolean call(ColumnElement columnElement) {
            return columnElement.isHandledRecursively()
                && columnElement.getReferencedTable().isQueryPartNeededForShallowQuery();
          }
        }, new ConditionCallback<ColumnElement>() {
          @Override
          public boolean call(ColumnElement columnElement) {
            return columnElement.isHandledRecursively()
                && columnElement.getReferencedTable().isImmutable()
                && columnElement.getReferencedTable().hasAnyNonIdNotNullableColumns();
          }
        });
  }

  private MethodSpec deepQueryPartsAddInternal(boolean fromSelection) {
    final Set<String> renameNeedingTables = new HashSet<>();
    final Set<String> duplicateTables = new HashSet<>();
    final Set<String> lookedTables = new HashSet<>();
    final Set<String> allGraphTables = new HashSet<>();

    for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
      if (!columnElement.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = columnElement.getReferencedTable();
      final String referencedTableName = referencedTable.getTableName();
      if (lookedTables.contains(referencedTableName)) {
        duplicateTables.add(referencedTableName);
      }

      ElementGraphWalker.depthFirst(referencedTable, getTableRenamesFindingVisitCallback(renameNeedingTables, allGraphTables, referencedTableName));

      lookedTables.add(referencedTableName);
    }
    renameNeedingTables.addAll(duplicateTables);

    return queryPartsAddInternal(fromSelection, METHOD_ADD_DEEP_QUERY_PARTS_INTERNAL,
        renameNeedingTables,
        new ConditionCallback<ColumnElement>() {
          @Override
          public boolean call(ColumnElement columnElement) {
            return columnElement.isHandledRecursively()
                && columnElement.getReferencedTable().hasAnyPersistedComplexColumns();
          }
        }, new ConditionCallback<ColumnElement>() {
          @Override
          public boolean call(ColumnElement columnElement) {
            return columnElement.isHandledRecursively();
          }
        });
  }

  private MethodSpec queryPartsAddInternal(boolean fromSelection,
                                           String methodName,
                                           final Set<String> renameNeedingTables,
                                           ConditionCallback<ColumnElement> isQueryPartsAddedRecursively,
                                           ConditionCallback<ColumnElement> isQueryPartAdded) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(tableType, "tableAlias")
        .addParameter(boolean.class, "renamedTableName")
        .addParameter(WriterUtil.LIST_JOIN_CLAUSE_TYPE_NAME, "joins");
    if (fromSelection) {
      builder.addParameter(selectFromTablesParam())
          .addParameter(systemRenamedTablesParam())
          .addParameter(tableGraphNodeNamesParam())
          .addParameter(String.class, "nodeName")
          .addParameter(select1Param());
    }
    if (fromSelection) {
      builder.addStatement("int index")
          .addStatement("String thisNodeName")
          .addStatement("$T containsTable", TypeName.BOOLEAN);
    }
    final Set<String> definedTableVariables = new HashSet<>(tableElement.getComplexColumnCount());
    boolean firstTime = true;
    int i = 0;
    for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
      if (isQueryPartAdded.call(columnElement)) {
        final TableElement referencedTable = columnElement.getReferencedTable();
        final ClassName referencedTableStructureType = getGeneratedTableStructureInterfaceName(referencedTable);
        final FormatData referencedTableStructureConstant = tableStructureConstant(referencedTable);
        final String referencedTableName = referencedTable.getTableName();

        if (!definedTableVariables.contains(referencedTableName)) {
          builder.addStatement(referencedTableStructureConstant.formatInto("final $T $L = %s"),
              referencedTableStructureConstant.getWithOtherArgsBefore(referencedTableStructureType, referencedTableName));
          definedTableVariables.add(referencedTableName);
        }

        final CodeBlock.Builder indexBuilder = CodeBlock.builder();
        if (firstTime) {
          firstTime = false;
          if (!fromSelection) {
            indexBuilder.add("int ");
          }
        }

        if (fromSelection) {
          if (!columnElement.isNullable()) {
            builder.addStatement("containsTable = selectFromTables.contains($L.name)", referencedTableName)
                .beginControlFlow("if (!select1 && !containsTable)")
                .addStatement("throw new $T(\"Column $L is not nullable and was not a part of selected columns\")",
                    SQL_EXCEPTION,
                    columnElement.getColumnName())
                .endControlFlow()
                .beginControlFlow("if (containsTable)");
          } else {
            builder.beginControlFlow("if (selectFromTables.contains($L.name))", referencedTableName);
          }
          builder.addStatement("thisNodeName = nodeName + $S", columnElement.getColumnName());
        }
        final String aliasedColumnVariableName = "aliasedColumn" + i;
        builder.addStatement(tableStructureConstant.formatInto("final $T $L = $T.internalCopy(tableAlias, %s.$L)"),
            tableStructureConstant.getArgsBetween(COLUMN, aliasedColumnVariableName, COLUMN)
                .and(columnFieldName(columnElement)));

        indexBuilder.add("index = $T.indexOf($L, joins, $L)",
            JOIN_CLAUSE, referencedTableName, aliasedColumnVariableName);
        builder.addCode(indexBuilder.add(codeBlockEnd()).build());
        if (fromSelection || isQueryPartsAddedRecursively.call(columnElement)) {
          builder.beginControlFlow("if (index != -1)");
          builder.addStatement("final $T userJoin = joins.get(index)", JOIN_CLAUSE);
          if (isQueryPartsAddedRecursively.call(columnElement)) {
            builder.addCode(callToComplexColumnQueryPartsInternalAdd(methodName, "userJoin.tableHasAlias()", referencedTable, fromSelection, new Callback<CodeBlock.Builder>() {
              @Override
              public void call(CodeBlock.Builder builder) {
                builder.add(referencedTableName);
              }
            }));
            if (fromSelection) {
              builder.addStatement("tableGraphNodeNames.put(thisNodeName, userJoin.tableNameInQuery())");
            }
          } else if (fromSelection) {
            builder.addStatement("tableGraphNodeNames.put(thisNodeName, userJoin.tableNameInQuery())");
          }
          builder.nextControlFlow("else");
        } else {
          builder.beginControlFlow("if (index == -1)");
        }
        final boolean renamedTableName = renameNeedingTables.contains(referencedTableName);
        if (!renamedTableName) {
          builder.beginControlFlow("if (renamedTableName)");
          addCreatedJoinAdd(builder, methodName, columnElement, fromSelection, true, aliasedColumnVariableName, isQueryPartsAddedRecursively);
          builder.nextControlFlow("else");
        }
        addCreatedJoinAdd(builder, methodName, columnElement, fromSelection, renamedTableName, aliasedColumnVariableName, isQueryPartsAddedRecursively);

        if (!renamedTableName) {
          builder.endControlFlow();
        }
        builder.endControlFlow();
        if (fromSelection) {
          builder.endControlFlow();
        }
        i++;
      }
    }
    return builder.build();
  }

  private void addCreatedJoinAdd(MethodSpec.Builder builder, String methodName,
                                 ColumnElement columnElement, boolean fromSelection,
                                 final boolean renameTable,
                                 String aliasedColumnVariableName,
                                 ConditionCallback<ColumnElement> isQueryPartsAddedRecursively) {
    final TableElement referencedTable = columnElement.getReferencedTable();
    final String referencedTableName = referencedTable.getTableName();
    if (renameTable) {
      builder.addStatement("final $T joinedTableAlias = $L.internalAlias($T.randomTableName())",
          ParameterizedTypeName.get(TABLE, referencedTable.getTableElementTypeName()),
          referencedTableName,
          UTIL);
      if (fromSelection) {
        builder.addStatement("final $T addedAlias = $T.addTableAlias(joinedTableAlias, systemRenamedTables)", String.class, UTIL)
            .addStatement("tableGraphNodeNames.put(thisNodeName, addedAlias)");
      }
    } else if (fromSelection) {
      builder.addStatement("final $T addedAlias = $T.addTableAlias($L, systemRenamedTables)", String.class, UTIL, referencedTableName)
          .addStatement("tableGraphNodeNames.put(thisNodeName, addedAlias)");
    }
    final FormatData tableStructureIdConstant = tableStructureIdConstant(referencedTable);
    if (renameTable) {
      builder.addStatement(tableStructureIdConstant.formatInto("final $T joinClause = joinedTableAlias.on($L.is($T.internalCopy(joinedTableAlias, %s)))"),
          tableStructureIdConstant.getWithOtherArgsBefore(JOIN_CLAUSE, aliasedColumnVariableName, COLUMN));
    } else {
      builder.addStatement(tableStructureIdConstant.formatInto("final $T joinClause = $L.on($L.is($T.internalCopy($L, %s)))"),
          tableStructureIdConstant.getWithOtherArgsBefore(JOIN_CLAUSE, referencedTableName, aliasedColumnVariableName, COLUMN, referencedTableName));
    }
    builder.addStatement("joinClause.operator = $T.LEFT_JOIN", FROM)
        .addStatement("joins.add(joinClause)");
    if (isQueryPartsAddedRecursively.call(columnElement)) {
      builder.addCode(callToComplexColumnQueryPartsInternalAdd(methodName, renameTable, referencedTable, fromSelection, new Callback<CodeBlock.Builder>() {
        @Override
        public void call(CodeBlock.Builder builder) {
          if (renameTable) {
            builder.add("joinedTableAlias");
          } else {
            builder.add("$L", referencedTableName);
          }
        }
      }));
    }
  }

  private CodeBlock callToComplexColumnQueryPartsInternalAdd(String methodName,
                                                             Object renamedTableName,
                                                             TableElement referencedTable,
                                                             boolean fromSelection,
                                                             Callback<CodeBlock.Builder> tableAliasAddCallback) {
    final CodeBlock.Builder builder = CodeBlock.builder()
        .add("$T.$L(",
            EntityEnvironment.getGeneratedHandlerClassName(referencedTable),
            methodName);
    tableAliasAddCallback.call(builder);
    builder.add(", $L, joins", renamedTableName);
    if (fromSelection) {
      builder.add(", selectFromTables, systemRenamedTables, tableGraphNodeNames, thisNodeName, select1");
    }
    builder.add(")").add(codeBlockEnd());
    return builder.build();
  }

  @NonNull
  private ElementGraphWalker.VisitCallback getTableRenamesFindingVisitCallback(final Set<String> renameNeedingTables, final Set<String> allGraphTables, final String referencedTableName) {
    return new ElementGraphWalker.VisitCallback() {
      @Override
      public void onVisit(TableElement tableElement) {
        final String tableName = tableElement.getTableName();
        if (allGraphTables.contains(tableName)) {
          renameNeedingTables.add(referencedTableName);
        }
        allGraphTables.add(tableName);
      }
    };
  }
}
