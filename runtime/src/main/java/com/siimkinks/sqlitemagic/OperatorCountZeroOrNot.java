package com.siimkinks.sqlitemagic;

import rx.Observable;
import rx.Subscriber;
import rx.exceptions.Exceptions;

final class OperatorCountZeroOrNot implements Observable.Operator<Boolean, Query<Long>> {
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

	final boolean countZero;

	OperatorCountZeroOrNot(boolean countZero) {
		this.countZero = countZero;
	}

	@Override
	public Subscriber<? super Query<Long>> call(final Subscriber<? super Boolean> subscriber) {
		return new Subscriber<Query<Long>>(subscriber) {
			@Override
			public void onNext(Query<Long> query) {
				try {
					final Long count = query.runImpl(subscriber, true);
					if (!subscriber.isUnsubscribed()) {
						subscriber.onNext(count > 0 ^ countZero ? Boolean.TRUE : Boolean.FALSE);
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
