package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import rx.Observable;
import rx.Subscriber;

/**
 * An {@link Observable} of {@link Query} which offers query-specific convenience operators.
 *
 * @param <R> Query return type
 */
public class QueryObservable<R> extends Observable<Query<R>> {
	QueryObservable(final Observable<Query<R>> o) {
		super(new OnSubscribe<Query<R>>() {
			@Override
			public void call(Subscriber<? super Query<R>> subscriber) {
				o.unsafeSubscribe(subscriber);
			}
		});
	}

	/**
	 * Runs each emitted {@linkplain Query query} and propagates its result(s) to
	 * downstream as a single item.
	 * <p>
	 * Be careful using this operator as it will always consume the entire cursor and create objects
	 * for each row, every time this observable emits a new query. On tables whose queries update
	 * frequently or very large result sets this can result in the creation of many objects.
	 * <p>
	 * This operator ignores {@code null} values returned from running {@link Query}.
	 */
	@NonNull
	@CheckResult
	public final Observable<R> runQuery() {
		return lift(OperatorRunQuery.<R>emitNotNull());
	}

	/**
	 * Runs each emitted {@link Query} and propagates its result(s) to downstream as a single item.
	 * <p>
	 * Be careful using this operator as it will always consume the entire cursor and create objects
	 * for each row, every time this observable emits a new query. On tables whose queries update
	 * frequently or very large result sets this can result in the creation of many objects.
	 * <p>
	 * This operator emits {@code defaultValue} if {@code null} is returned from running {@link Query}.
	 *
	 * @param defaultValue Value emitted if {@link Query} result is {@code null}
	 */
	@NonNull
	@CheckResult
	public final Observable<R> runQueryOrDefault(R defaultValue) {
		return lift(OperatorRunQuery.emitNotNullOrDefault(defaultValue));
	}

	/**
	 * Runs only the first emitted {@link Query} and propagates its result(s) to downstream
	 * as a single item.
	 * <p>
	 * This operator ignores {@code null} values returned from running {@link Query}.
	 */
	@NonNull
	@CheckResult
	public final Observable<R> runQueryOnce() {
		return first().lift(OperatorRunQuery.<R>emitNotNull());
	}

	/**
	 * Runs only the first emitted {@link Query} and propagates its result(s) to downstream
	 * as a single item.
	 * <p>
	 * This operator emits {@code defaultValue} if {@code null} is returned from running {@link Query}.
	 *
	 * @param defaultValue Value emitted if {@link Query} result is {@code null}
	 */
	@NonNull
	@CheckResult
	public final Observable<R> runQueryOnceOrDefault(R defaultValue) {
		return first().lift(OperatorRunQuery.emitNotNullOrDefault(defaultValue));
	}
}
