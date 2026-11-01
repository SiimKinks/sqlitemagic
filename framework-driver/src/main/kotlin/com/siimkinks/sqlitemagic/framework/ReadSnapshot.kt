package com.siimkinks.sqlitemagic.framework

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement as FrameworkProgram

internal class ReadSnapshot(private val db: SQLiteDatabase) {
  private var holders = 0

  fun join() = when {
    holders > 0 -> {
      holders++
      true
    }
    else -> false
  }

  fun begin() = when {
    db.inTransaction() || !queryOnly() -> false
    else -> {
      execute(BEGIN_SNAPSHOT)
      holders = 1
      true
    }
  }

  fun end() {
    holders--
    if (holders == 0) {
      execute(END_SNAPSHOT)
    }
  }

  private fun queryOnly() = db
    .compileStatement(QUERY_ONLY)
    .use(FrameworkProgram::simpleQueryForLong) == 1L

  private fun execute(sql: String) = db
    .compileStatement(sql)
    .use(FrameworkProgram::execute)
}

private const val QUERY_ONLY = "PRAGMA query_only"
private const val BEGIN_SNAPSHOT = "SAVEPOINT read_snapshot"
private const val END_SNAPSHOT = "RELEASE read_snapshot"
