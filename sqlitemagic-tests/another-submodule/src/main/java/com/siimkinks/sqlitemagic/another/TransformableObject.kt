package com.siimkinks.sqlitemagic.another

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

data class TransformableObject(val value: Int)

@ObjectToDbValue
fun objectToDbValue(javaObject: TransformableObject): Int {
  return javaObject.value
}

@DbValueToObject
fun dbValueToObject(dbObject: Int): TransformableObject {
  return TransformableObject(dbObject)
}