package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import java.util.*

object DbTransformers {
  @JvmStatic
  @DbValueToObject
  fun dbValueToDate(dbValue: Long?): Date? = dbValue?.let(::Date)

  @JvmStatic
  @ObjectToDbValue
  fun dateToDbValue(date: Date?): Long? = date?.time
}