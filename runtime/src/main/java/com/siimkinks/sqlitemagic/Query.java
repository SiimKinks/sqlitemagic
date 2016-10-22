package com.siimkinks.sqlitemagic;

import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;

import static rx.exceptions.Exceptions.throwOrReport;

/**
 * An executable query.
 */
public abstract class Query<T> {
  static final Subscription INFINITE_SUBSCRIPTION = new Subscription() {
    @Override
    public void unsubscribe() {
      // ignore
    }

    @Override
    public boolean isUnsubscribed() {
      return false;
    }
  };
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
    return runImpl(INFINITE_SUBSCRIPTION, true);
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
    return Observable.create(new Observable.OnSubscribe<T>() {
      @Override
      public void call(Subscriber<? super T> subscriber) {
        try {
          final T result = runImpl(subscriber, true);
          if (!subscriber.isUnsubscribed()) {
            if (result != null) {
              subscriber.onNext(result);
            }
            if (!subscriber.isUnsubscribed()) {
              subscriber.onCompleted();
            }
          }
        } catch (Throwable t) {
          throwOrReport(t, subscriber, Query.this.toString());
        }
      }
    });
  }

  /**
   * Executes this query against a database.
   *
   * @param subscriber Subscriber
   * @param inStream   Whether query is executed in observable stream or synchronously
   * @return Query result
   */
  @CallSuper
  T runImpl(@NonNull Subscription subscriber, boolean inStream) {
    if (inStream && dbConnection.transactions.get() != null) {
      throw new IllegalStateException("Cannot execute observable query in a transaction.");
    }
    return null;
  }
}
