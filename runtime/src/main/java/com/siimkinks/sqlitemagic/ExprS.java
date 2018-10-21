package com.siimkinks.sqlitemagic;

import com.siimkinks.sqlitemagic.Select.Select1;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class ExprS extends Expr {
  @NonNull
  private final SelectBuilder<Select1> selectBuilder;
  @Nullable
  private ArrayList<String> parentObservedTables;

  ExprS(@NonNull Column<?, ?, ?, ?, ?> column,
        @NonNull String op,
        @NonNull SelectSqlNode.SelectNode<?, Select1, ?> selectNode) {
    super(column, op);
    this.selectBuilder = selectNode.selectBuilder;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    super.addArgs(args);
    args.addAll(selectBuilder.args);
  }

  @Override
  void addObservedTables(@NonNull ArrayList<String> tables) {
    super.addObservedTables(tables);
    // defer observed tables adding until sql building, where we might add missing joins,
    // so there will be more tables to add/observe
    this.parentObservedTables = tables;
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    super.appendToSql(sb);
    sb.append('(');
    selectBuilder.appendCompiledQuery(sb, parentObservedTables);
    sb.append(')');
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    super.appendToSql(sb, systemRenamedTables);
    sb.append('(');
    selectBuilder.appendCompiledQuery(sb, parentObservedTables);
    sb.append(')');
  }
}
