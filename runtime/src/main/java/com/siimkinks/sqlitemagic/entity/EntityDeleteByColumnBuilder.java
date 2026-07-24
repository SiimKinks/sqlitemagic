package com.siimkinks.sqlitemagic.entity;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import com.siimkinks.sqlitemagic.Column;
import com.siimkinks.sqlitemagic.NotNullable;
import com.siimkinks.sqlitemagic.Unique;

import io.reactivex.Single;

/**
 * Builder for a delete operation that identifies an entity row by a provided unique column.
 *
 * @param <P> Parent table type
 */
public interface EntityDeleteByColumnBuilder<P> extends ConnectionProvidedOperation<EntityDeleteByColumnBuilder<P>> {
  /**
   * Execute this configured delete operation against a database using the provided column
   * to identify the entity row.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param <C>      Not nullable unique column type
   * @return Nr of deleted records
   */
  <C extends Column<?, ?, ?, P, NotNullable> & Unique<NotNullable>> int execute(
      @NonNull C byColumn
  );

  /**
   * Creates a {@link Single} that when subscribed to executes this configured delete operation
   * against a database using the provided column to identify the entity row and emits nr of
   * deleted records to downstream only once.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param <C>      Not nullable unique column type
   * @return Deferred {@link Single} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  <C extends Column<?, ?, ?, P, NotNullable> & Unique<NotNullable>> Single<Integer> observe(
      @NonNull C byColumn
  );
}
