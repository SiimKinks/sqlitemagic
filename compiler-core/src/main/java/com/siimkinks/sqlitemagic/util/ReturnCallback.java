package com.siimkinks.sqlitemagic.util;

public interface ReturnCallback<R, I> {
  R call(I obj);
}
