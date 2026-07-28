package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult

interface EntityPersistOperationBuilder<R> : EntityOperationBuilder<R> {
  /**
   * Configure this operation to ignore `null` values inside entities while persisting them.
   *
   * @return Operation builder
   */
  @CheckResult
  fun ignoreNullValues(): R
}
