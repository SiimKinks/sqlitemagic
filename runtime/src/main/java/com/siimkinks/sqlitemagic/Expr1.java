package com.siimkinks.sqlitemagic;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

final class Expr1 extends Expr {
  @Nullable
  private final String evalArg;

  Expr1(@NonNull Column<?, ?, ?, ?, ?> column, @NonNull String op, @Nullable String evalArg) {
    super(column, op);
    this.evalArg = evalArg;
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    args.add(evalArg);
  }
}
