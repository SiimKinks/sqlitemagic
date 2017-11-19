package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.NotNullable;
import com.siimkinks.sqlitemagic.Unique;

public interface EntityUpdateByColumnBuilder<R> {
  /**
   * Configure this operation to be executed by the provided column.
   * Executed update operation will be performed using "WHERE column=?" clause.
   *
   * @param column Column by which to execute the operation. This must be one of annotation
   *               processor generated column objects that correspond to a column in a database
   *               table
   * @param <C>    Not nullable unique column
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  <C extends Unique<NotNullable>> R byColumn(C column);
}
