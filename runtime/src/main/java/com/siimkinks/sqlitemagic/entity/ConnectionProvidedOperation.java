package com.siimkinks.sqlitemagic.entity;

import com.siimkinks.sqlitemagic.DbConnection;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public interface ConnectionProvidedOperation<R> {
  /**
   * Configure this operation to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  R usingConnection(@NonNull DbConnection connection);
}
