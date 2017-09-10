package com.siimkinks.sqlitemagic.entity;


import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.ConflictAlgorithm;

interface EntityOperationBuilder<R> extends ConnectionProvidedOperation<R> {
  /**
   * Configure this operation to use provided conflict algorithm.
   *
   * @param conflictAlgorithm One of {@link android.database.sqlite.SQLiteDatabase}
   *                          CONFLICT_* constant values
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  R conflictAlgorithm(@ConflictAlgorithm int conflictAlgorithm);
}
