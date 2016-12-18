package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

final class ExprR extends Expr {
  @NonNull
  private final String[] evalArgs;

  ExprR(@NonNull String expr,
        @NonNull String[] evalArgs) {
    //noinspection ConstantConditions
    super(null, expr);
    this.evalArgs = evalArgs;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    Collections.addAll(args, evalArgs);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    sb.append(expr);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append(expr);
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
    return expr.contains(column.nameInQuery);
  }
}
