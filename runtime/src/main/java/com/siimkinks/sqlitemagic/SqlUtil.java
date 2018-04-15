package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;
import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.ArrayList;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CLEAR_DATA;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_COLUMN_FOR_VALUE;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CREATE_SCHEMA;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_DB_NAME;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_DB_VERSION;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_NR_OF_TABLES;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_GET_SUBMODULE_NAMES;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_IS_DEBUG;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_MIGRATE_VIEWS;

/**
 * Internal utility functions.
 */
public final class SqlUtil {
  private SqlUtil() {
    throw new AssertionError("no instances");
  }

  @Invokes(INVOCATION_METHOD_CREATE_SCHEMA)
  public static void createSchema(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @NonNull
  @Invokes(INVOCATION_METHOD_CLEAR_DATA)
  public static StringArraySet clearData(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_MIGRATE_VIEWS)
  public static void migrateViews(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Nullable
  @Invokes(INVOCATION_METHOD_GET_SUBMODULE_NAMES)
  public static String[] getSubmoduleNames() {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_GET_NR_OF_TABLES)
  public static int getNrOfTables(@Nullable String moduleName) {
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
  public static <V> Column<V, V, V, ?, NotNullable> columnForValue(@NonNull V val) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  @Invokes(INVOCATION_METHOD_IS_DEBUG)
  public static boolean isDebug() {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  static void createView(@NonNull SupportSQLiteDatabase db,
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

  @NonNull
  @CheckResult
  static String opByColumnSql(@NonNull String sql,
                              @NonNull String tableName,
                              @Nullable ArrayList<Column> byColumns) {
    if (byColumns == null) {
      return sql;
    }
    Column column = firstColumnForTable(tableName, byColumns);
    if (column == null) {
      return sql;
    }
    final int whereClauseEndIndex = sql.indexOf("WHERE") + 6;
    return new StringBuilder(whereClauseEndIndex + 20)
        .append(sql, 0, whereClauseEndIndex)
        .append(column.name)
        .append("=?")
        .toString();
  }

  @Nullable
  static Column firstColumnForTable(@NonNull String tableName,
                                    @NonNull ArrayList<Column> columns) {
    Column column = null;
    final int size = columns.size();
    for (int i = 0; i < size; i++) {
      final Column c = columns.get(i);
      if (c.table.name.equals(tableName)) {
        column = c;
        break;
      }
    }
    return column;
  }

  static void bindAllArgsAsStrings(@NonNull SupportSQLiteStatement statement, @Nullable String[] args) {
    if (args != null) {
      for (int i = args.length; i != 0; i--) {
        statement.bindString(i, args[i - 1]);
      }
    }
  }
}
