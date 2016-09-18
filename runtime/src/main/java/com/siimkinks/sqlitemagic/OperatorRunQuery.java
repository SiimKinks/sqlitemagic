package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import rx.Observable;
import rx.Subscriber;
import rx.exceptions.Exceptions;

final class OperatorRunQuery<T> implements Observable.Operator<T, Query<T>> {
	private static class Holder {
		final static OperatorRunQuery<Object> INSTANCE = new OperatorRunQuery<>(false, null);
	}

	@NonNull
	@CheckResult
	@SuppressWarnings("unchecked")
	static <T> OperatorRunQuery<T> emitNotNull() {
		return (OperatorRunQuery<T>) Holder.INSTANCE;
	}

	@NonNull
	@CheckResult
	static <T> OperatorRunQuery<T> emitNotNullOrDefault(T defaultValue) {
		return new OperatorRunQuery<>(true, defaultValue);
	}

	final boolean emitDefault;
	final T defaultValue;

	OperatorRunQuery(boolean emitDefault, T defaultValue) {
		this.emitDefault = emitDefault;
		this.defaultValue = defaultValue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Subscriber<? super Query<T>> call(final Subscriber<? super T> subscriber) {
		return new Subscriber<Query<T>>(subscriber) {
			@Override
			public void onNext(Query<T> query) {
				try {
					final T item = query.runImpl(subscriber, true);
					if (!subscriber.isUnsubscribed()) {
						if (item != null) {
							subscriber.onNext(item);
						} else if (emitDefault) {
							subscriber.onNext(defaultValue);
						} else {
							request(1);
						}
					}
				} catch (Throwable e) {
					Exceptions.throwOrReport(e, this, query.toString());
				}
			}

			@Override
			public void onCompleted() {
				subscriber.onCompleted();
			}

			@Override
			public void onError(Throwable e) {
				subscriber.onError(e);
			}
		};
	}
}
