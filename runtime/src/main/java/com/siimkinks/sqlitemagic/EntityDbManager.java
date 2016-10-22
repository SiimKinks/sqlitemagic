package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

final class EntityDbManager {
  private final AtomicReference<SQLiteStatement> insertStatement = new AtomicReference<>();
  private final AtomicReference<SQLiteStatement> updateStatement = new AtomicReference<>();
  @Nullable
  private DbConnectionImpl dbConnection;

  EntityDbManager(@NonNull DbConnectionImpl dbConnection) {
    this.dbConnection = dbConnection;
  }

  void close() {
    final SQLiteStatement insertStm = insertStatement.getAndSet(null);
    if (insertStm != null) {
      insertStm.close();
    }
    final SQLiteStatement updateStm = updateStatement.getAndSet(null);
    if (updateStm != null) {
      updateStm.close();
    }
    dbConnection = null;
  }

  @NonNull
  @CheckResult
  DbConnectionImpl getDbConnection() {
    if (dbConnection == null) {
      throw new IllegalStateException("DB connection closed");
    }
    return dbConnection;
  }

  @NonNull
  @CheckResult
  SQLiteStatement getInsertStatement(@NonNull String insertSql) {
    SQLiteStatement stm = insertStatement.get();
    if (stm == null) {
      if (dbConnection == null) {
        throw new IllegalStateException("DB connection closed");
      }
      stm = dbConnection.compileStatement(insertSql);
      insertStatement.set(stm);
      return stm;
    }
    return stm;
  }

  @NonNull
  @CheckResult
  SQLiteStatement getUpdateStatement(@NonNull String updateSql) {
    SQLiteStatement stm = updateStatement.get();
    if (stm == null) {
      if (dbConnection == null) {
        throw new IllegalStateException("DB connection closed");
      }
      stm = dbConnection.compileStatement(updateSql);
      updateStatement.set(stm);
      return stm;
    }
    return stm;
  }
}
