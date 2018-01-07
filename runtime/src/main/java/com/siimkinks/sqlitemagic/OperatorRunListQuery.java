package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.ObservableOperator;
import io.reactivex.Observer;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.plugins.RxJavaPlugins;

final class OperatorRunListQuery<T> implements ObservableOperator<List<T>, Query<List<T>>> {
  private static class Holder {
    final static OperatorRunListQuery<Object> INSTANCE = new OperatorRunListQuery<>();
  }

  @NonNull
  @CheckResult
  @SuppressWarnings("unchecked")
  static <T> OperatorRunListQuery<T> instance() {
    return (OperatorRunListQuery<T>) Holder.INSTANCE;
  }

  @Override
  public Observer<? super Query<List<T>>> apply(Observer<? super List<T>> observer) throws Exception {
    return new MappingObserver<>(observer);
  }

  static final class MappingObserver<T> extends DisposableObserver<Query<List<T>>> {
    @NonNull
    private final Observer<? super List<T>> downstream;

    MappingObserver(@NonNull Observer<? super List<T>> downstream) {
      this.downstream = downstream;
    }

    @Override
    protected void onStart() {
      downstream.onSubscribe(this);
    }

    @Override
    public void onNext(Query<List<T>> query) {
      try {
        final Cursor cursor = query.rawQuery(true);
        if (cursor == null || isDisposed()) {
          return;
        }
        final List<T> items;
        try {
          final int rowCount = cursor.getCount();
          if (rowCount == 0) {
            items = Collections.emptyList();
          } else {
            final Query.Mapper<T> mapper = ((Query.DatabaseQuery<List<T>, T>) query).mapper;
            if (mapper == null) {
              throw new IllegalStateException("Query needs a mapper -- this is a SqliteMagic bug");
            }
            items = new ArrayList<>(rowCount);
            while (cursor.moveToNext() && !isDisposed()) {
              items.add(mapper.apply(cursor));
            }
          }
        } finally {
          cursor.close();
        }
        if (!isDisposed()) {
          downstream.onNext(items);
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
