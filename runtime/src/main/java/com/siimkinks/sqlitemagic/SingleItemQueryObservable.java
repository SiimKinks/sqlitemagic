package com.siimkinks.sqlitemagic;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;

/**
 * An {@link Observable} of {@link Query} which offers single item query specific convenience operators.
 *
 * @param <R> Mapped query return value type
 */
public class SingleItemQueryObservable<R> extends Observable<Query<R>> {
  private final Observable<Query<R>> upstream;

  public SingleItemQueryObservable(Observable<Query<R>> upstream) {
    this.upstream = upstream;
  }

  @Override
  protected void subscribeActual(Observer<? super Query<R>> observer) {
    upstream.subscribe(observer);
  }

  /**
   * Transform each emitted {@link Query} which returns a single row to {@code R}.
   * <p>
   * This operator ignores queries with empty result.
   */
  @NonNull
  @CheckResult
  public final Observable<R> runQuery() {
    return lift(OperatorRunSingleItemQuery.<R>emitNotNull());
  }

  /**
   * Transform each emitted {@link Query} which returns a single row to {@code R}.
   * <p>
   * This operator emits {@code defaultValue} if query result is empty.
   *
   * @param defaultValue Value emitted if {@link Query} result is empty
   */
  @NonNull
  @CheckResult
  public final Observable<R> runQueryOrDefault(R defaultValue) {
    return lift(OperatorRunSingleItemQuery.emitNotNullOrDefault(defaultValue));
  }

  /**
   * Transform only the first emitted {@link Query} which returns a single row to {@code R}.
   * <p>
   * This operator ignores query with an empty result, resulting in an empty stream.
   */
  @NonNull
  @CheckResult
  public final Maybe<R> runQueryOnce() {
    return take(1).lift(OperatorRunSingleItemQuery.<R>emitNotNull()).elementAt(0);
  }

  /**
   * Transform only the first emitted {@link Query} which returns a single row to {@code R}.
   * <p>
   * This operator emits {@code defaultValue} if query result is empty.
   *
   * @param defaultValue Value emitted if {@link Query} result is empty
   */
  @NonNull
  @CheckResult
  public final Single<R> runQueryOnceOrDefault(R defaultValue) {
    return take(1).lift(OperatorRunSingleItemQuery.emitNotNullOrDefault(defaultValue)).firstOrError();
  }
}
