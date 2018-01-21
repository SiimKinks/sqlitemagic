package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;

import java.util.Date;

public final class DateTransformer {
  @ObjectToDbValue
  public static Long objectToDbValue(Date javaObject) {
    return javaObject == null ? null : javaObject.getTime();
  }

  @DbValueToObject
  public static Date dbValueToObject(Long dbObject) {
    return dbObject == null ? null : new Date(dbObject);
  }
}