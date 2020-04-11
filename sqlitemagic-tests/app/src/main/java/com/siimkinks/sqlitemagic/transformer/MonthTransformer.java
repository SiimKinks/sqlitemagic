package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;

import org.threeten.bp.Month;

public class MonthTransformer {
  @ObjectToDbValue
  public static String objectToDbValue(Month javaObject) {
    return javaObject == null ? null : javaObject.name();
  }

  @DbValueToObject
  public static Month dbValueToObject(String dbObject) {
    return dbObject == null ? null : Month.valueOf(dbObject);
  }
}
