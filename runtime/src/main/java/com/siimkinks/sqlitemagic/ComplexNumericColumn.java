package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils.ValueParser;

/**
 * A relationship column whose ID is stored with a numeric SQLite affinity.
 *
 * @param <ID> Referenced model ID type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 * @param <N>  Column nullability
 */
public class ComplexNumericColumn<ID, R, ET, P, N> extends NumericColumn<ID, R, ET, P, N> {
  ComplexNumericColumn(
      @NonNull Table<P> table,
      @NonNull String name,
      boolean allFromTable,
      @NonNull ValueParser<?> valueParser,
      boolean nullable,
      @Nullable String alias
  ) {
    super(table, name, allFromTable, valueParser, nullable, alias);
  }
}
