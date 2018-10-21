package com.siimkinks.sqlitemagic;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.reactivex.Single;

import static com.siimkinks.sqlitemagic.SqlUtil.bindAllArgsAsStrings;

/**
 * Compiled SQL DELETE statement.
 */
public final class CompiledDelete {
  @NonNull
  private final SupportSQLiteStatement deleteStm;
  @NonNull
  private final String tableName;
  @NonNull
  private final DbConnectionImpl dbConnection;

  CompiledDelete(@NonNull SupportSQLiteStatement deleteStm,
                 @NonNull String tableName,
                 @NonNull DbConnectionImpl dbConnection) {
    this.deleteStm = deleteStm;
    this.tableName = tableName;
    this.dbConnection = dbConnection;
  }

  /**
   * Execute this compiled delete statement against a database.
   * <p>
   * This method runs synchronously in the calling thread.
   *
   * @return Number of deleted rows
   */
  @WorkerThread
  public int execute() {
    final int affectedRows;
    synchronized (deleteStm) {
      affectedRows = deleteStm.executeUpdateDelete();
    }
    if (affectedRows > 0) {
      dbConnection.sendTableTrigger(tableName);
    }
    return affectedRows;
  }

  /**
   * Creates a {@link Single} that when subscribed to executes this compiled
   * delete statement against a database and emits nr of deleted records to downstream
   * only once.
   *
   * @return Deferred {@link Single} that when subscribed to executes the statement and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  public Single<Integer> observe() {
    return Single.fromCallable(new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return execute();
      }
    });
  }

  static final class Builder {
    DeleteSqlNode sqlTreeRoot;
    int sqlNodeCount;
    Delete.From from;
    final ArrayList<String> args = new ArrayList<>();
    DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    @NonNull
    @CheckResult
    CompiledDelete build() {
      final String sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount);
      final SupportSQLiteStatement stm = dbConnection.compileStatement(sql);
      bindAllArgsAsStrings(stm, args.toArray(new String[args.size()]));
      return new CompiledDelete(stm, from.tableName, dbConnection);
    }
  }
}
