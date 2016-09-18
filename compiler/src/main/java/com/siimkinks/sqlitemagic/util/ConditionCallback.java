package com.siimkinks.sqlitemagic.util;

public interface ConditionCallback<T> {
	boolean call(T obj);

	ConditionCallback ALWAYS_TRUE = new ConditionCallback() {
		@Override
		public boolean call(Object obj) {
			return true;
		}
	};
}
