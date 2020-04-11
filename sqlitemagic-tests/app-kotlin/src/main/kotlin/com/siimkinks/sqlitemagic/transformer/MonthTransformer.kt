package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import org.threeten.bp.Month

@ObjectToDbValue
fun objectToDbValue(javaObject: Month): String = javaObject.name

@DbValueToObject
fun dbValueToObject(dbObject: String): Month = Month
    .values()
    .firstOrNull { it.name == dbObject }
    ?: Month.JANUARY