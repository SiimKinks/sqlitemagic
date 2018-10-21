package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A unique column used in queries and conditions.
 *
 * @param <T>  Exact type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 * @param <N>  Column nullability
 */
public final class UniqueColumn<T, R, ET, P, N> extends Column<T, R, ET, P, N> implements Unique<N> {
  UniqueColumn(@NonNull Table<P> table,
               @NonNull String name,
               boolean allFromTable,
               @NonNull Utils.ValueParser<?> valueParser,
               boolean nullable,
               @Nullable String alias) {
    super(table, name, allFromTable, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  public UniqueColumn<T, R, ET, P, N> as(@NonNull String alias) {
    return new UniqueColumn<>(table, name, allFromTable, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  public <NewTableType> UniqueColumn<T, R, ET, NewTableType, N> inTable(@NonNull Table<NewTableType> table) {
    return new UniqueColumn<>(table, name, allFromTable, valueParser, nullable, alias);
  }
}
