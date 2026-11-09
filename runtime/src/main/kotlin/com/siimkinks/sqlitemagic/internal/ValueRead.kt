package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement

@InternalSqliteMagicApi
fun interface ValueRead<out T> {
  operator fun invoke(statement: SQLiteStatement, index: Int): T
}
