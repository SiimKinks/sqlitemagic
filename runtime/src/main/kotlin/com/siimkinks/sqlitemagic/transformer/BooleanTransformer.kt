package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

/** Transformer for `boolean` data types. */
object BooleanTransformer {
  @ObjectToDbValue
  fun objectToDbValue(javaObject: Boolean?) = when {
    javaObject == null -> null
    javaObject -> 1
    else -> 0
  }

  @DbValueToObject
  fun dbValueToObject(dbObject: Int?) = dbObject?.equals(1)
}
