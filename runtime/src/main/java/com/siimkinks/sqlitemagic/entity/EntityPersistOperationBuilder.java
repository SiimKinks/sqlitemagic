package com.siimkinks.sqlitemagic.entity;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

interface EntityPersistOperationBuilder<R> extends EntityOperationBuilder<R> {
  /**
   * Configure this operation to ignore {@code null} values inside entities while persisting them.
   *
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  R ignoreNullValues();
}
