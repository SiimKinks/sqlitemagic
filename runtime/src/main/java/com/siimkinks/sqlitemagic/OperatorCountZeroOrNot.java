package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import io.reactivex.ObservableOperator;
import io.reactivex.Observer;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.plugins.RxJavaPlugins;

final class OperatorCountZeroOrNot implements ObservableOperator<Boolean, Query<Long>> {
  private static class Holder {
    final static OperatorCountZeroOrNot COUNT_ZERO = new OperatorCountZeroOrNot(true);
    final static OperatorCountZeroOrNot COUNT_NOT_ZERO = new OperatorCountZeroOrNot(false);
  }

  static OperatorCountZeroOrNot countZero() {
    return Holder.COUNT_ZERO;
  }

  static OperatorCountZeroOrNot countNotZero() {
    return Holder.COUNT_NOT_ZERO;
  }

  private final boolean countZero;

  OperatorCountZeroOrNot(boolean countZero) {
    this.countZero = countZero;
  }

  @Override
  public Observer<? super Query<Long>> apply(Observer<? super Boolean> observer) throws Exception {
    return new MappingObserver(observer, countZero);
  }

  static final class MappingObserver extends DisposableObserver<Query<Long>> {
    @NonNull
    private final Observer<? super Boolean> downstream;
    private final boolean countZero;

    MappingObserver(@NonNull Observer<? super Boolean> downstream, boolean countZero) {
      this.downstream = downstream;
      this.countZero = countZero;
    }

    @Override
    protected void onStart() {
      downstream.onSubscribe(this);
    }

    @Override
    public void onNext(Query<Long> query) {
      try {
        // returns null every time, but is needed for transaction checks
        final SqliteMagicCursor cursor = query.rawQuery(true);
        final Long count = query.map(cursor);
        if (!isDisposed()) {
          downstream.onNext(count > 0 ^ countZero ? Boolean.TRUE : Boolean.FALSE);
        }
      } catch (Throwable e) {
        Exceptions.throwIfFatal(e);
        onError(e);
      }
    }

    @Override
    public void onComplete() {
      if (!isDisposed()) {
        downstream.onComplete();
      }
    }

    @Override
    public void onError(Throwable e) {
      if (isDisposed()) {
        RxJavaPlugins.onError(e);
      } else {
        downstream.onError(e);
      }
    }
  }
}
