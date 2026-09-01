package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

data class ExternalToken(val value: String)

object ExternalTransformer {
  @ObjectToDbValue
  fun externalTokenToDb(value: ExternalToken): String = value.value

  @DbValueToObject
  fun dbToExternalToken(value: String): ExternalToken = ExternalToken(value)
}
