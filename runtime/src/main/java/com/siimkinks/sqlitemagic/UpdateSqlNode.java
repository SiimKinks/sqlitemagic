package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.LinkedList;

import rx.Single;

abstract class UpdateSqlNode extends SqlNode {
  @NonNull
  final CompiledUpdate.Builder updateBuilder;

  UpdateSqlNode(@Nullable UpdateSqlNode parent) {
    super(parent);
    final CompiledUpdate.Builder updateBuilder;
    if (parent != null) {
      updateBuilder = parent.updateBuilder;
    } else {
      updateBuilder = new CompiledUpdate.Builder();
    }
    updateBuilder.sqlTreeRoot = this;
    updateBuilder.sqlNodeCount++;
    this.updateBuilder = updateBuilder;
  }

  @Override
  protected final void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    throw new UnsupportedOperationException();
  }

  public static abstract class ExecutableNode extends UpdateSqlNode implements ConnectionProvidedOperation<ExecutableNode> {
    ExecutableNode(@NonNull UpdateSqlNode parent) {
      super(parent);
    }

    @NonNull
    @Override
    public final ExecutableNode usingConnection(@NonNull DbConnection connection) {
      updateBuilder.dbConnection = (DbConnectionImpl) connection;
      return this;
    }

    /**
     * Compiles this SQL UPDATE statement.
     * <p>
     * Result object is immutable and can be shared between threads without any side effects.
     *
     * @return Immutable compiled delete statement
     */
    @NonNull
    @CheckResult
    public final CompiledUpdate compile() {
      return updateBuilder.build();
    }

    /**
     * Execute this compiled update statement against a database.
     * <p>
     * This method runs synchronously in the calling thread.
     *
     * @return Number of updated rows
     */
    @WorkerThread
    public final int execute() {
      return updateBuilder.build().execute();
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
    public final Single<Integer> observe() {
      return updateBuilder.build().observe();
    }
  }
}
