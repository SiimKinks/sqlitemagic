package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection

interface ConnectionProvidedOperation<R> {
  /**
   * Configure this operation to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return Operation builder
   */
  @CheckResult
  fun usingConnection(connection: DbConnection): R
}
