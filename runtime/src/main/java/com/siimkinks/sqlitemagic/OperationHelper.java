package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.io.Closeable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_NONE;
import static com.siimkinks.sqlitemagic.SqlUtil.opByColumnSql;

public final class OperationHelper implements Closeable {
  @IntDef({
      Op.INSERT, // insert
      Op.UPDATE, // update
      Op.PERSIST // persist
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface Op {
    int INSERT = 0;
    int UPDATE = 1;
    int PERSIST = 2;
  }

  @ConflictAlgorithm
  private final int conflictAlgorithm;
  private final boolean customSqlNeededForConflictAlgorithm;
  private final boolean customSqlNeededForUpdates;
  private SimpleArrayMap<String, SQLiteStatement> inserts;
  private SimpleArrayMap<String, SQLiteStatement> updates;
  final boolean ignoreConflict;
  @Nullable
  private final ArrayList<Column> operationByColumns;

  OperationHelper(@ConflictAlgorithm int conflictAlgorithm,
                  @Op int op,
                  @Nullable ArrayList<Column> operationByColumns) {
    this.conflictAlgorithm = conflictAlgorithm;
    this.ignoreConflict = conflictAlgorithm == CONFLICT_IGNORE;
    this.operationByColumns = operationByColumns;
    this.customSqlNeededForConflictAlgorithm = conflictAlgorithm != CONFLICT_NONE && conflictAlgorithm != CONFLICT_ABORT;
    this.customSqlNeededForUpdates = op != Op.INSERT && operationByColumns != null && !operationByColumns.isEmpty();
    if (customSqlNeededForConflictAlgorithm) {
      switch (op) {
        case 0:
          inserts = new SimpleArrayMap<>(SimpleArrayMap.BASE_SIZE);
          break;
        case 1:
          updates = new SimpleArrayMap<>(SimpleArrayMap.BASE_SIZE);
          break;
        case 2:
          inserts = new SimpleArrayMap<>(SimpleArrayMap.BASE_SIZE);
          updates = new SimpleArrayMap<>(SimpleArrayMap.BASE_SIZE);
          break;
        default:
          break;
      }
    }
    if (!customSqlNeededForConflictAlgorithm && customSqlNeededForUpdates) {
      updates = new SimpleArrayMap<>(SimpleArrayMap.BASE_SIZE);
    }
  }

  @NonNull
  @CheckResult
  SQLiteStatement getInsertStatement(@NonNull String tableName,
                                     @NonNull String sql,
                                     @NonNull EntityDbManager manager) {
    if (customSqlNeededForConflictAlgorithm) {
      SQLiteStatement insert = inserts.get(tableName);
      if (insert == null) {
        insert = manager.compileStatement(sql, conflictAlgorithm);
        inserts.put(tableName, insert);
      }
      return insert;
    }
    return manager.getInsertStatement(sql);
  }

  @NonNull
  @CheckResult
  SQLiteStatement getUpdateStatement(@NonNull String tableName,
                                     @NonNull String sql,
                                     @NonNull EntityDbManager manager) {
    if (customSqlNeededForConflictAlgorithm || customSqlNeededForUpdates) {
      SQLiteStatement update = updates.get(tableName);
      if (update == null) {
        update = manager.compileStatement(opByColumnSql(sql, tableName, operationByColumns), conflictAlgorithm);
        updates.put(tableName, update);
      }
      return update;
    }
    return manager.getUpdateStatement(sql);
  }

  @Override
  public void close() {
    final SimpleArrayMap<String, SQLiteStatement> inserts = this.inserts;
    if (inserts != null) {
      this.inserts = null;
      closeStatements(inserts);
    }
    final SimpleArrayMap<String, SQLiteStatement> updates = this.updates;
    if (updates != null) {
      this.updates = null;
      closeStatements(updates);
    }
  }

  private static void closeStatements(@NonNull SimpleArrayMap<String, SQLiteStatement> statements) {
    final int size = statements.size();
    for (int i = 0; i < size; i++) {
      statements.valueAt(i).close();
    }
  }
}
