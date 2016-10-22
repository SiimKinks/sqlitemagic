package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import rx.Single;

/**
 * Compiled SQL UPDATE statement.
 */
public final class CompiledUpdate {
  @NonNull
  private final SQLiteStatement updateStm;
  @NonNull
  private final String tableName;
  @NonNull
  private final DbConnectionImpl dbConnection;

  CompiledUpdate(@NonNull SQLiteStatement updateStm,
                 @NonNull String tableName,
                 @NonNull DbConnectionImpl dbConnection) {
    this.updateStm = updateStm;
    this.tableName = tableName;
    this.dbConnection = dbConnection;
  }

  /**
   * Execute this compiled update statement against a database.
   * <p>
   * This method runs synchronously in the calling thread.
   *
   * @return Number of updated rows
   */
  @WorkerThread
  public int execute() {
    final int affectedRows;
    synchronized (updateStm) {
      affectedRows = updateStm.executeUpdateDelete();
    }
    if (affectedRows > 0) {
      dbConnection.sendTableTrigger(tableName);
    }
    return affectedRows;
  }

  /**
   * Creates a {@link Single} that when subscribed to executes this compiled
   * update statement against a database and emits nr of updated records to downstream
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
    UpdateSqlNode sqlTreeRoot;
    int sqlNodeCount;
    Update.TableNode tableNode;
    final ArrayList<String> args = new ArrayList<>();
    DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    @NonNull
    @CheckResult
    CompiledUpdate build() {
      final String sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount);
      final SQLiteStatement stm = dbConnection.compileStatement(sql);
      stm.bindAllArgsAsStrings(args.toArray(new String[args.size()]));
      return new CompiledUpdate(stm, tableNode.table.nameInQuery, dbConnection);
    }
  }
}
