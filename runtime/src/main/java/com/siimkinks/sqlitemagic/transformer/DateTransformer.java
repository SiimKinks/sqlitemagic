package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;

import java.util.Date;

/**
 * Transformer for {@code java.util.Date} data types.
 */
@Transformer
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
