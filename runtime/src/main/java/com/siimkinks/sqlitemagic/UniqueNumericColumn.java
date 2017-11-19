package com.siimkinks.sqlitemagic;

import android.support.annotation.*;
import android.support.annotation.Nullable;

/**
 * A unique numeric column used in queries and conditions.
 *
 * @param <T>  Exact type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 * @param <N>  Column nullability
 */
public final class UniqueNumericColumn<T, R, ET, P, N> extends NumericColumn<T, R, ET, P, N> implements Unique<N> {
  UniqueNumericColumn(@NonNull Table<P> table,
                      @NonNull String name,
                      boolean allFromTable,
                      @NonNull Utils.ValueParser<?> valueParser,
                      boolean nullable,
                      @Nullable String alias) {
    super(table, name, allFromTable, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  public UniqueNumericColumn<T, R, ET, P, N> as(@NonNull String alias) {
    return new UniqueNumericColumn<>(table, name, allFromTable, valueParser, nullable, alias);
  }
}
