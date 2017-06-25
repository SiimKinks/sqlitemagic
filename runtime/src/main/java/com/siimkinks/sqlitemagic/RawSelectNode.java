package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.RawSelect.CompiledRawSelectImpl;
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation;

@SuppressWarnings("unchecked")
class RawSelectNode<R, CompiledType extends CompiledRawSelect> implements ConnectionProvidedOperation<R> {
  @NonNull
  final RawSelect.Builder rawSelectBuilder;

  RawSelectNode(@NonNull RawSelect.Builder rawSelectBuilder) {
    this.rawSelectBuilder = rawSelectBuilder;
  }

  @NonNull
  @Override
  public final R usingConnection(@NonNull DbConnection connection) {
    rawSelectBuilder.dbConnection = (DbConnectionImpl) connection;
    return (R) this;
  }

  /**
   * Define SQL arguments.
   *
   * @param args
   *     Arguments for the created SQL
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  public final R withArgs(@NonNull @Size(min = 1) String... args) {
    rawSelectBuilder.args = args;
    return (R) this;
  }

  /**
   * Compiles this SQL SELECT statement.
   * <p>
   * Result object is immutable and can be shared between threads without any side effects.
   * <p>
   * Note: This method does not compile underlying SQL statement.
   *
   * @return Immutable compiled raw SQL SELECT statement
   */
  @CheckResult
  public final CompiledType compile() {
    return (CompiledType) new CompiledRawSelectImpl(rawSelectBuilder);
  }

  /**
   * Compile and execute this raw SELECT statement against a database.
   * <p>
   * This method runs synchronously in the calling thread.
   *
   * @return {@link Cursor} over the result set
   */
  @NonNull
  @CheckResult
  @WorkerThread
  public final Cursor execute() {
    return new CompiledRawSelectImpl(rawSelectBuilder).execute();
  }
}
