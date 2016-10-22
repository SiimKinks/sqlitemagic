package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
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
}
