package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteStatement;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.transformer.BooleanTransformer;

@SuppressWarnings("unchecked")
public final class BooleanColumn<T, N> extends NumericColumn<Boolean, Boolean, Boolean, T, N> {
  BooleanColumn(@NonNull Table<T> table,
                @NonNull String name,
                @NonNull Utils.ValueParser valueParser,
                boolean nullable,
                @Nullable String alias) {
    super(table, name, false, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  String toSqlArg(@NonNull Boolean val) {
    final Integer sqlVal = BooleanTransformer.objectToDbValue(val);
    if (sqlVal == null) {
      throw new NullPointerException("SQL argument cannot be null");
    }
    return sqlVal.toString();
  }

  @NonNull
  @Override
  public BooleanColumn<T, N> as(@NonNull String alias) {
    return new BooleanColumn<>(table, name, valueParser, nullable, alias);
  }

  @Nullable
  @Override
  <V> V getFromCursor(@NonNull Cursor cursor) {
    final Integer dbVal = super.getFromCursor(cursor);
    return (V) BooleanTransformer.dbValueToObject(dbVal);
  }

  @Nullable
  @Override
  Boolean getFromStatement(@NonNull SupportSQLiteStatement stm) {
    final Integer dbVal = super.getFromStatement(stm);
    return BooleanTransformer.dbValueToObject(dbVal);
  }
}
