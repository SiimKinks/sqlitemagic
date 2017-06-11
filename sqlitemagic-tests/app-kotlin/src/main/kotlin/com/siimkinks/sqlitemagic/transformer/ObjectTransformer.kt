package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer
import com.siimkinks.sqlitemagic.model.TransformableObject

@Transformer
object ObjectTransformer {
  @JvmStatic
  @ObjectToDbValue
  fun objectToDbValue(javaObject: TransformableObject): Int {
    return javaObject.value
  }

  @JvmStatic
  @DbValueToObject
  fun dbValueToObject(dbObject: Int): TransformableObject {
    return TransformableObject(dbObject)
  }
}