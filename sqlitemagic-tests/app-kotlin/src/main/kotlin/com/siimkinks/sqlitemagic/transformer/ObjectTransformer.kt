package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import com.siimkinks.sqlitemagic.model.TransformableObject

@ObjectToDbValue
fun objectToDbValue(javaObject: TransformableObject): Int {
  return javaObject.value
}

@DbValueToObject
fun dbValueToObject(dbObject: Int): TransformableObject {
  return TransformableObject(dbObject)
}