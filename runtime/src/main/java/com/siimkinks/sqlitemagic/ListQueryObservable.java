package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;

/**
 * An {@link Observable} of {@link Query} which offers list query specific convenience operators.
 *
 * @param <R>
 *     Mapped query result list element type
 */
public class ListQueryObservable<R> extends Observable<Query<List<R>>> {
  private final Observable<Query<List<R>>> upstream;

  public ListQueryObservable(Observable<Query<List<R>>> upstream) {
    this.upstream = upstream;
  }

  @Override
  protected void subscribeActual(Observer<? super Query<List<R>>> observer) {
    upstream.subscribe(observer);
  }

  /**
   * Transform each emitted {@link Query} to a {@code List<R>}.
   * <p>
   * Be careful using this operator as it will always consume the entire cursor and create objects
   * for each row, every time this observable emits a new query. On tables whose queries update
   * frequently or very large result sets this can result in the creation of many objects.
   */
  @NonNull
  @CheckResult
  public final Observable<List<R>> runQuery() {
    return lift(OperatorRunListQuery.<R>instance());
  }

  /**
   * Transform only the first emitted {@link Query} to a {@code List<R>}.
   */
  @NonNull
  @CheckResult
  public final Single<List<R>> runQueryOnce() {
    return take(1).lift(OperatorRunListQuery.<R>instance()).firstOrError();
  }
}
