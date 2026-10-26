package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.parameterEncoding

internal fun <T> testTable(
  name: String,
  alias: String? = null,
  temporary: Boolean = false
): Table<T> = object : Table<T>(
  name = name,
  alias = alias,
  temporary = temporary
) {}

internal fun <M> testStringKey(table: Table<M>, name: String) =
  UniqueColumn<M, String, Required, String, String, Required, String>(
    table = table,
    name = name,
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
