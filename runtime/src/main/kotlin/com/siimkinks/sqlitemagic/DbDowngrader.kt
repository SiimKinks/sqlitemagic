package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase

fun interface DbDowngrader {
  fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int)
}
