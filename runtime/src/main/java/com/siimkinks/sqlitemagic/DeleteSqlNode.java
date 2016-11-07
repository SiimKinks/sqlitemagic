package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.LinkedList;

import rx.Single;

abstract class DeleteSqlNode extends SqlNode {
  @NonNull
  final CompiledDelete.Builder deleteBuilder;

  DeleteSqlNode(@Nullable DeleteSqlNode parent) {
    super(parent);
    final CompiledDelete.Builder deleteBuilder;
    if (parent != null) {
      deleteBuilder = parent.deleteBuilder;
    } else {
      deleteBuilder = new CompiledDelete.Builder();
    }
    deleteBuilder.sqlTreeRoot = this;
    deleteBuilder.sqlNodeCount++;
    this.deleteBuilder = deleteBuilder;
  }

  @Override
  protected final void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    throw new UnsupportedOperationException();
  }

  public static abstract class ExecutableNode extends DeleteSqlNode implements ConnectionProvidedOperation<ExecutableNode> {
    ExecutableNode(@NonNull DeleteSqlNode parent) {
      super(parent);
    }

    @NonNull
    @Override
    public ExecutableNode usingConnection(@NonNull DbConnection connection) {
      deleteBuilder.dbConnection = (DbConnectionImpl) connection;
      return this;
    }

    /**
     * Compiles this SQL DELETE statement.
     * <p>
     * Result object is immutable and can be shared between threads without any side effects.
     *
     * @return Immutable compiled delete statement
     */
    @NonNull
    @CheckResult
    public final CompiledDelete compile() {
      return deleteBuilder.build();
    }

    /**
     * Compile and execute this delete statement against a database.
     * <p>
     * This method runs synchronously in the calling thread.
     *
     * @return Number of deleted rows
     */
    @WorkerThread
    public final int execute() {
      return deleteBuilder.build().execute();
    }

    /**
     * Compiles this delete statement and creates a {@link Single} that when subscribed to
     * executes this statement against a database and emits nr of deleted records to downstream
     * only once.
     *
     * @return Deferred {@link Single} that when subscribed to executes the statement and emits
     * its result to downstream
     */
    @NonNull
    @CheckResult
    public final Single<Integer> observe() {
      return deleteBuilder.build().observe();
    }
  }
}
