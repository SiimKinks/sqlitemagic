package com.siimkinks.sqlitemagic.transformer;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.model.TransformableObject;

public class ObjectTransformer {

  @NonNull
  @ObjectToDbValue
  public static Integer objectToDbValue(TransformableObject javaObject) {
    return javaObject.value;
  }

  @NonNull
  @DbValueToObject
  public static TransformableObject dbValueToObject(Integer dbObject) {
    return new TransformableObject(dbObject);
  }
}
