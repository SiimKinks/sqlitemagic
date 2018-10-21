package com.siimkinks.sqlitemagic;

import android.database.Cursor;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.ObservableOperator;
import io.reactivex.Observer;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.plugins.RxJavaPlugins;

final class OperatorRunSingleItemQuery<T> implements ObservableOperator<T, Query<T>> {
  private static class Holder {
    final static OperatorRunSingleItemQuery<Object> INSTANCE = new OperatorRunSingleItemQuery<>(null);
  }

  @NonNull
  @CheckResult
  @SuppressWarnings("unchecked")
  static <T> OperatorRunSingleItemQuery<T> emitNotNull() {
    return (OperatorRunSingleItemQuery<T>) Holder.INSTANCE;
  }

  @NonNull
  @CheckResult
  static <T> OperatorRunSingleItemQuery<T> emitNotNullOrDefault(T defaultValue) {
    return new OperatorRunSingleItemQuery<>(defaultValue);
  }

  @Nullable
  private final T defaultValue;

  /**
   * A null {@code defaultValue} means nothing will be emitted when empty.
   */
  OperatorRunSingleItemQuery(@Nullable T defaultValue) {
    this.defaultValue = defaultValue;
  }

  @Override
  public Observer<? super Query<T>> apply(Observer<? super T> observer) throws Exception {
    return new MappingObserver<>(observer, defaultValue);
  }

  static final class MappingObserver<T> extends DisposableObserver<Query<T>> {
    @NonNull
    private final Observer<? super T> downstream;
    @Nullable
    private final T defaultValue;

    MappingObserver(@NonNull Observer<? super T> downstream, @Nullable T defaultValue) {
      this.downstream = downstream;
      this.defaultValue = defaultValue;
    }

    @Override
    protected void onStart() {
      downstream.onSubscribe(this);
    }

    @Override
    public void onNext(Query<T> query) {
      try {
        // null cursor here is valid
        final Cursor cursor = query.rawQuery(true);
        final T item = query.map(cursor);
        if (!isDisposed()) {
          if (item != null) {
            downstream.onNext(item);
          } else if (defaultValue != null) {
            downstream.onNext(defaultValue);
          }
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
