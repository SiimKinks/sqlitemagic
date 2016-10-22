package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedList;

final class BinaryExpr extends Expr {
  @NonNull
  final Expr lhs;
  @NonNull
  final Expr rhs;

  BinaryExpr(@NonNull String expr, @NonNull Expr lhs, @NonNull Expr rhs) {
    //noinspection ConstantConditions
    super(null, expr); // null is ok here, we override SQL appending
    this.lhs = lhs;
    this.rhs = rhs;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    lhs.addArgs(args);
    rhs.addArgs(args);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    sb.append('(');
    lhs.appendToSql(sb);
    sb.append(expr);
    rhs.appendToSql(sb);
    sb.append(')');
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append('(');
    lhs.appendToSql(sb, systemRenamedTables);
    sb.append(expr);
    rhs.appendToSql(sb, systemRenamedTables);
    sb.append(')');
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
    final boolean lhsContains = lhs.containsColumn(column);
    return lhsContains || rhs.containsColumn(column);
  }
}
