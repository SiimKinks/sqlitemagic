package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import io.reactivex.Single;

/**
 * Builder for bulk delete operation.
 */
public interface EntityBulkDeleteBuilder extends ConnectionProvidedOperation<EntityBulkDeleteBuilder> {
  /**
   * Execute this configured bulk delete operation against a database.
   *
   * @return Nr of deleted records
   */
  int execute();

  /**
   * Creates a {@link Single} that when subscribed to executes this configured bulk
   * delete operation against a database and emits nr of deleted records to downstream
   * only once.
   *
   * @return Deferred {@link Single} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Single<Integer> observe();
}
