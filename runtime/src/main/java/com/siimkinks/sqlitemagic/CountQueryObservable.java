package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import rx.Observable;

import static com.siimkinks.sqlitemagic.OperatorCountZeroOrNot.countNotZero;
import static com.siimkinks.sqlitemagic.OperatorCountZeroOrNot.countZero;

/**
 * An {@link Observable} of {@link Query} which offers count query specific convenience operators.
 */
public final class CountQueryObservable extends QueryObservable<Long> {
  CountQueryObservable(Observable<Query<Long>> o) {
    super(o);
  }

  /**
   * Runs each emitted {@link Query} and propagates {@code true} to downstream if
   * {@link Query} result is equal to zero; {@code false} otherwise.
   */
  @NonNull
  @CheckResult
  public final Observable<Boolean> isZero() {
    return lift(countZero());
  }

  /**
   * Runs each emitted {@link Query} and propagates {@code true} to downstream if
   * {@link Query} result is not equal to zero; {@code false} otherwise.
   */
  @NonNull
  @CheckResult
  public final Observable<Boolean> isNotZero() {
    return lift(countNotZero());
  }
}
