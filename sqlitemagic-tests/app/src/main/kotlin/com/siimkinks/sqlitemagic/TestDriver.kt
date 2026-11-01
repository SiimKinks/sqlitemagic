package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.siimkinks.sqlitemagic.BuildConfig.SQLITE_ENGINE
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver

internal val runsOnBundledSqlite = when (SQLITE_ENGINE) {
  "bundled" -> true
  "framework" -> false
  else -> error("Unknown sqliteEngine $SQLITE_ENGINE; use framework or bundled")
}

internal fun testDriver(): SQLiteDriver = when {
  runsOnBundledSqlite -> BundledSQLiteDriver()
  else -> FrameworkSQLiteDriver()
}
