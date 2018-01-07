package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

final class EntityDbManager {
  private final AtomicReference<SupportSQLiteStatement> insertStatement = new AtomicReference<>();
  private final AtomicReference<SupportSQLiteStatement> updateStatement = new AtomicReference<>();
  @Nullable
  private DbConnectionImpl dbConnection;

  EntityDbManager(@NonNull DbConnectionImpl dbConnection) {
    this.dbConnection = dbConnection;
  }

  void close() {
    final SupportSQLiteStatement insertStm = insertStatement.getAndSet(null);
    if (insertStm != null) {
      try {
        insertStm.close();
      } catch (Exception ignore) {}
    }
    final SupportSQLiteStatement updateStm = updateStatement.getAndSet(null);
    if (updateStm != null) {
      try {
        updateStm.close();
      } catch (Exception ignore) {}
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
  SupportSQLiteStatement getInsertStatement(@NonNull String insertSql) {
    SupportSQLiteStatement stm = insertStatement.get();
    if (stm == null) {
      if (dbConnection == null) {
        throw new IllegalStateException("DB connection closed");
      }
      stm = dbConnection.compileStatement(String.format(insertSql, ""));
      insertStatement.set(stm);
      return stm;
    }
    return stm;
  }

  @NonNull
  @CheckResult
  SupportSQLiteStatement getUpdateStatement(@NonNull String updateSql) {
    SupportSQLiteStatement stm = updateStatement.get();
    if (stm == null) {
      if (dbConnection == null) {
        throw new IllegalStateException("DB connection closed");
      }
      stm = dbConnection.compileStatement(String.format(updateSql, ""));
      updateStatement.set(stm);
      return stm;
    }
    return stm;
  }

  @NonNull
  @CheckResult
  SupportSQLiteStatement compileStatement(@NonNull String sql) {
    final DbConnectionImpl dbConnection = this.dbConnection;
    if (dbConnection == null) {
      throw new IllegalStateException("DB connection closed");
    }
    return dbConnection.compileStatement(sql);
  }

  @NonNull
  @CheckResult
  SupportSQLiteStatement compileStatement(@NonNull String sql, @ConflictAlgorithm int conflictAlgorithm) {
    final DbConnectionImpl dbConnection = this.dbConnection;
    if (dbConnection == null) {
      throw new IllegalStateException("DB connection closed");
    }
    return dbConnection.compileStatement(String.format(sql, ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm]));
  }
}
