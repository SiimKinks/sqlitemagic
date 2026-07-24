package com.siimkinks.sqlitemagic.entity;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import com.siimkinks.sqlitemagic.Column;
import com.siimkinks.sqlitemagic.NotNullable;
import com.siimkinks.sqlitemagic.Unique;

import io.reactivex.Completable;

/**
 * Builder for a bulk update operation that identifies each entity row by a provided unique column.
 *
 * @param <P> Parent table type
 */
public interface EntityBulkUpdateByColumnBuilder<P> extends EntityOperationBuilder<EntityBulkUpdateByColumnBuilder<P>> {
  /**
   * Execute this configured bulk update operation against a database using the provided column
   * to identify each entity row.
   * Operation will be executed inside a transaction.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param <C>      Not nullable unique column type
   * @return {@code true} if the operation was successful; {@code false} when some operation failed
   * and this operation was rolled back.
   */
  <C extends Column<?, ?, ?, P, NotNullable> & Unique<NotNullable>> boolean execute(
      @NonNull C byColumn
  );

  /**
   * Creates a {@link Completable} that when subscribed to executes this configured bulk update
   * operation against a database using the provided column to identify each entity row.
   * Operation will be executed inside a transaction.
   * If the operation was successful then complete will be emitted to downstream.
   * If the operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param <C>      Not nullable unique column type
   * @return Deferred {@link Completable} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  <C extends Column<?, ?, ?, P, NotNullable> & Unique<NotNullable>> Completable observe(
      @NonNull C byColumn
  );
}
