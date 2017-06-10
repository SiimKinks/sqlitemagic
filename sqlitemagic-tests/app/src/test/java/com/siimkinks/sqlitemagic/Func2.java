package com.siimkinks.sqlitemagic;

public interface Func2<T1, T2, R> {
  R call(T1 t1, T2 t2);
}
