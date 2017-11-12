package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.LinkedList;

final class ExprC extends Expr {
  @NonNull
  private final Column<?, ?, ?, ?, ?> exprColumn;

  ExprC(@NonNull Column<?, ?, ?, ?, ?> column, @NonNull String op, @NonNull Column<?, ?, ?, ?, ?> exprColumn) {
    super(column, op);
    this.exprColumn = exprColumn;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    super.addArgs(args);
    exprColumn.addArgs(args);
  }

  @Override
  void addObservedTables(@NonNull ArrayList<String> tables) {
    super.addObservedTables(tables);
    exprColumn.addObservedTables(tables);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    super.appendToSql(sb);
    if (exprColumn.hasAlias()) {
      sb.append(exprColumn.getAppendableAlias());
    } else {
      exprColumn.appendSql(sb);
    }
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    super.appendToSql(sb, systemRenamedTables);
    if (exprColumn.hasAlias()) {
      sb.append(exprColumn.getAppendableAlias());
    } else {
      exprColumn.appendSql(sb, systemRenamedTables);
    }
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?, ?> column) {
    return super.containsColumn(column) || column.equals(exprColumn);
  }
}
