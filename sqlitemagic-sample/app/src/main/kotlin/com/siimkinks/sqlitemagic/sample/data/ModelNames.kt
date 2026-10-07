package com.siimkinks.sqlitemagic.sample.data

internal fun validName(value: String) = value.trim().also {
  require(it.isNotEmpty()) { "Enter a name." }
}
