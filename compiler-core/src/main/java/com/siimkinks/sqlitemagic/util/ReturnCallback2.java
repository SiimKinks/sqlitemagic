package com.siimkinks.sqlitemagic.util;

public interface ReturnCallback2<R, I1, I2> {
  R call(I1 o1, I2 o2);
}
