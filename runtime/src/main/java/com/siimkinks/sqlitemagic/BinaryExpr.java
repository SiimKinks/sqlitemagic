package com.siimkinks.sqlitemagic;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.LinkedList;

import androidx.annotation.NonNull;

final class BinaryExpr extends Expr {
  @NonNull
  final Expr lhs;
  @NonNull
  final Expr rhs;

  BinaryExpr(@NonNull String op, @NonNull Expr lhs, @NonNull Expr rhs) {
    //noinspection ConstantConditions
    super(null, op); // null is ok here, we override all calls related to column
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
    sb.append(op);
    rhs.appendToSql(sb);
    sb.append(')');
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append('(');
    lhs.appendToSql(sb, systemRenamedTables);
    sb.append(op);
    rhs.appendToSql(sb, systemRenamedTables);
    sb.append(')');
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?, ?> column) {
    return lhs.containsColumn(column) || rhs.containsColumn(column);
  }
}
