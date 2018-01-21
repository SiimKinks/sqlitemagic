package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

data class SubmoduleTransformableObject(val value: Int)

@ObjectToDbValue
fun objectToDbValue(javaObject: SubmoduleTransformableObject): Int {
  return javaObject.value
}

@DbValueToObject
fun dbValueToObject(dbObject: Int): SubmoduleTransformableObject {
  return SubmoduleTransformableObject(dbObject)
}