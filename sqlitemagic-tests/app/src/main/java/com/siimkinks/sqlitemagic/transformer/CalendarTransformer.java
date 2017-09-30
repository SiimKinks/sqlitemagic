package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;

import java.util.Calendar;

public final class CalendarTransformer {
  @ObjectToDbValue
  public static Long objectToDbValue(Calendar javaObject) {
    if (javaObject != null) {
      return javaObject.getTimeInMillis();
    }
    return null;
  }

  @DbValueToObject
  public static Calendar dbValueToObject(Long dbObject) {
    if (dbObject != null) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(dbObject);
      return calendar;
    }
    return null;
  }
}
