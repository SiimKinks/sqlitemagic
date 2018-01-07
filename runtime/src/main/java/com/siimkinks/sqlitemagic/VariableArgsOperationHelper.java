package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;

public final class VariableArgsOperationHelper {
  private final String conflictAlgorithm;
  private StringBuilder sqlBuilder;
  @OperationHelper.Op
  private int lastCompiledOperation = -1;
  final boolean ignoreConflict;

  VariableArgsOperationHelper(@ConflictAlgorithm int conflictAlgorithm) {
    this.conflictAlgorithm = ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm];
    ignoreConflict = conflictAlgorithm == CONFLICT_IGNORE;
  }

  @NonNull
  @CheckResult
  SupportSQLiteStatement compileStatement(@OperationHelper.Op int operation,
                                          @NonNull String tableName,
                                          int maxColumns,
                                          @NonNull SimpleArrayMap<String, Object> values,
                                          @NonNull String resolutionColumn,
                                          @NonNull EntityDbManager manager) {
    StringBuilder sqlBuilder = this.sqlBuilder;
    if (sqlBuilder == null) {
      sqlBuilder = new StringBuilder(7 + conflictAlgorithm.length() + maxColumns * 22);
      this.sqlBuilder = sqlBuilder;
    }
    final int lastCompiledOperation = this.lastCompiledOperation;
    this.lastCompiledOperation = operation;

    return compileStatement(
        operation == lastCompiledOperation,
        operation,
        sqlBuilder,
        conflictAlgorithm,
        tableName,
        values,
        resolutionColumn,
        manager);
  }

  @NonNull
  @CheckResult
  private static SupportSQLiteStatement compileStatement(boolean reuseOperation,
                                                         @OperationHelper.Op int operation,
                                                         @NonNull StringBuilder sqlBuilder,
                                                         @NonNull String conflictAlgorithm,
                                                         @NonNull String tableName,
                                                         @NonNull SimpleArrayMap<String, Object> values,
                                                         @Nullable String resolutionColumn,
                                                         @NonNull EntityDbManager manager) {
    final int valuesSize = values.size();
    if (operation == OperationHelper.Op.INSERT) {
      if (reuseOperation) {
        sqlBuilder.setLength(/*INSERT INTO */ 12 + conflictAlgorithm.length());
      } else {
        sqlBuilder.setLength(0);
        sqlBuilder
            .append("INSERT")
            .append(conflictAlgorithm)
            .append(" INTO ");
      }
      sqlBuilder
          .append(tableName)
          .append('(');
      int i;
      for (i = 0; i < valuesSize; i++) {
        if (i > 0) {
          sqlBuilder.append(',');
        }
        sqlBuilder.append(values.keyAt(i));
      }
      sqlBuilder.append(") VALUES (");
      for (i = 0; i < valuesSize; i++) {
        if (i > 0) {
          sqlBuilder.append(",?");
        } else {
          sqlBuilder.append('?');
        }
      }
      sqlBuilder.append(')');
    } else {
      if (reuseOperation) {
        sqlBuilder.setLength(/*UPDATE */ 7 + conflictAlgorithm.length());
      } else {
        sqlBuilder.setLength(0);
        sqlBuilder
            .append("UPDATE")
            .append(conflictAlgorithm)
            .append(' ');
      }
      sqlBuilder
          .append(tableName)
          .append(" SET ");
      for (int i = 0; i < valuesSize; i++) {
        if (i > 0) {
          sqlBuilder.append(',');
        }
        sqlBuilder
            .append(values.keyAt(i))
            .append("=?");
      }

      sqlBuilder
          .append(" WHERE ")
          .append(resolutionColumn)
          .append("=?");
    }

    final SupportSQLiteStatement statement = manager.compileStatement(sqlBuilder.toString());
    int i;
    for (i = 0; i < valuesSize; i++) {
      final Object value = values.valueAt(i);
      bindValue(statement, i + 1, value);
    }
    if (operation == OperationHelper.Op.UPDATE) {
      final Object value = values.get(resolutionColumn);
      if (value == null) {
        throw new NullPointerException("Column \"" + resolutionColumn + "\" is null");
      }
      bindValue(statement, i + 1, value);
    }
    return statement;
  }

  private static void bindValue(@NonNull SupportSQLiteStatement statement, int pos, Object value) {
    if (value instanceof String) {
      statement.bindString(pos, (String) value);
    } else if (value instanceof Number) {
      if (value instanceof Float || value instanceof Double) {
        statement.bindDouble(pos, ((Number) value).doubleValue());
      } else {
        statement.bindLong(pos, ((Number) value).longValue());
      }
    } else if (value instanceof byte[]) {
      statement.bindBlob(pos, (byte[]) value);
    } else if (value instanceof Byte[]) {
      statement.bindBlob(pos, Utils.toByteArray((Byte[]) value));
    } else {
      statement.bindString(pos, value.toString());
    }
  }
}
