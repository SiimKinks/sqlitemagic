package com.siimkinks.sqlitemagic;

import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.internal.MutableInt;

import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;

/**
 * An executable query.
 */
public abstract class Query<T> {
  @NonNull
  final DbConnectionImpl dbConnection;

  Query(@NonNull DbConnectionImpl dbConnection) {
    this.dbConnection = dbConnection;
  }

  /**
   * Execute this query against a database and return the resulting data.
   * This method runs synchronously in the calling thread.
   * <p>
   * Depending on the query type and result data type structure this method
   * can return {@code null}.
   * Rule of thumb is: If the result type is {@link java.util.List} it will
   * never be {@code null} otherwise it might be {@code null}.
   *
   * @return This query result
   */
  @CheckResult
  @WorkerThread
  public final T runBlocking() {
    return map(rawQuery(true));
  }

  /**
   * Creates {@link Observable} that when subscribed to executes this query against a database
   * and emits query result to downstream only once.
   * <p>
   * The resulting observable will be empty if query result is {@code null}.
   * <dl>
   * <dt><b>Scheduler:</b></dt>
   * <dd>{@code run} does not operate by default on a particular {@link Scheduler}.</dd>
   * </dl>
   *
   * @return Deferred {@link Observable} that when subscribed to executes the query and emits
   * its result to downstream
   * @see #runBlocking
   */
  @NonNull
  @CheckResult
  public final Observable<T> run() {
    return Observable.create(new ObservableOnSubscribe<T>() {
      @Override
      public void subscribe(ObservableEmitter<T> emitter) throws Exception {
        final SqliteMagicCursor cursor = rawQuery(true);
        if (emitter.isDisposed()) {
          return;
        }
        final T result = map(cursor);
        if (result != null) {
          emitter.onNext(result);
        }
        emitter.onComplete();
      }
    });
  }

  /**
   * Executes this query against a database.
   *
   * @param inStream
   *     Whether query is executed in observable stream or synchronously
   * @return Query result, maybe {@code null}
   */
  @CallSuper
  SqliteMagicCursor rawQuery(boolean inStream) {
    if (inStream && dbConnection.transactions.get() != null) {
      throw new IllegalStateException("Cannot execute observable query in a transaction.");
    }
    return null;
  }

  abstract T map(SqliteMagicCursor cursor);

  static abstract class DatabaseQuery<T, E> extends Query<T> implements Function<Set<String>, Query<T>> {
    @Nullable
    final Mapper<E> mapper;

    DatabaseQuery(@NonNull DbConnectionImpl dbConnection, @Nullable Mapper<E> mapper) {
      super(dbConnection);
      this.mapper = mapper;
    }

    @Override
    public Query<T> apply(Set<String> __) throws Exception {
      return this;
    }
  }

  interface Mapper<R> {
    R apply(@NonNull FastCursor cursor);
  }

  static abstract class MapperWithColumnOffset<R> implements Mapper<R> {
    final MutableInt columnOffset = new MutableInt();
  }
}
