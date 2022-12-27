package com.siimkinks.sqlitemagic;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import java.util.ArrayList;

/**
 * Internal utility functions.
 */
public final class SqlUtil {
  private SqlUtil() {
    throw new AssertionError("no instances");
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
