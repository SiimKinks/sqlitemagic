package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

class TransformableObject(val value: Int)

@ObjectToDbValue
fun objectToDbValue(javaObject: TransformableObject): Int =
  javaObject.value

@DbValueToObject
fun dbValueToObject(dbObject: Int): TransformableObject =
  TransformableObject(dbObject)