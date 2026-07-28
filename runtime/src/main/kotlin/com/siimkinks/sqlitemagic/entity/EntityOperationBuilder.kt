package com.siimkinks.sqlitemagic.entity

import android.database.sqlite.SQLiteDatabase
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.ConflictAlgorithm

interface EntityOperationBuilder<R> : ConnectionProvidedOperation<R> {
  /**
   * Configure this operation to use provided conflict algorithm.
   *
   * @param conflictAlgorithm One of [SQLiteDatabase] CONFLICT_* constant values
   * @return Operation builder
   */
  @CheckResult
  fun conflictAlgorithm(@ConflictAlgorithm conflictAlgorithm: Int): R
}
