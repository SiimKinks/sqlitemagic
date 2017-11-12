package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

final class ExprR extends Expr {
  @NonNull
  private final String[] evalArgs;

  ExprR(@NonNull String op,
        @NonNull String[] evalArgs) {
    //noinspection ConstantConditions
    super(null, op);
    this.evalArgs = evalArgs;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    Collections.addAll(args, evalArgs);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    sb.append(op);
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append(op);
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?, ?> column) {
    return op.contains(column.nameInQuery);
  }
}
