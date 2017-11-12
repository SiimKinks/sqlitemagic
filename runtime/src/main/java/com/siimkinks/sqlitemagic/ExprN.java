package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;

final class ExprN extends Expr {
  @NonNull
  private final String[] evalArgs;

  ExprN(@NonNull Column<?, ?, ?, ?, ?> column, @NonNull String op, @NonNull String[] evalArgs) {
    super(column, op);
    this.evalArgs = evalArgs;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    Collections.addAll(args, evalArgs);
  }
}
