package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CLEAR_DATA;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_COLUMN_FOR_VALUE;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_DB_NAME;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_DB_VERSION;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_NR_OF_TABLES;

/**
 * Internal utility functions.
 */
public final class SqlUtil {
  private SqlUtil() {
    throw new AssertionError("no instances");
  }

  @Invokes(INVOCATION_METHOD_CLEAR_DATA)
  @Nullable
  public static String[] clearData(SQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_GET_NR_OF_TABLES)
  public static int getNrOfTables() {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_GET_DB_VERSION)
  public static int getDbVersion() {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_GET_DB_NAME)
  public static String getDbName() {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_COLUMN_FOR_VALUE)
  public static <V> Column<V, V, V, ?> columnForValue(@NonNull V val) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  static void createView(@NonNull SQLiteDatabase db,
                         @NonNull CompiledSelect query,
                         @NonNull String viewName) {
    final CompiledSelectImpl queryImpl = (CompiledSelectImpl) query;
    final String[] args = queryImpl.args;
    if (args != null) {
      db.execSQL("CREATE VIEW IF NOT EXISTS " + viewName + " AS " + queryImpl.sql, args);
    } else {
      db.execSQL("CREATE VIEW IF NOT EXISTS " + viewName + " AS " + queryImpl.sql);
    }
  }
}
