package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.LinkedList;

final class UnaryExpr extends Expr {
  @NonNull
  final Expr expr;

  UnaryExpr(@NonNull String op, @NonNull Expr expr) {
    //noinspection ConstantConditions
    super(null, op); // null is ok here, we override all calls related to column
    this.expr = expr;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    expr.addArgs(args);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    sb.append(op);
    sb.append('(');
    expr.appendToSql(sb);
    sb.append(')');
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append(op);
    sb.append('(');
    expr.appendToSql(sb, systemRenamedTables);
    sb.append(')');
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?, ?> column) {
    return expr.containsColumn(column);
  }
}
