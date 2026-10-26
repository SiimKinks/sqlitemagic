package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

internal class DefaultDbDowngrader(
  private val database: GeneratedDatabase
) : DbDowngrader {
  override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    logDebug { "Downgrading database from $oldVersion to $newVersion" }
    db.query(PERSISTENT_SCHEMA_OBJECTS).use { cursor ->
      while (cursor.moveToNext()) {
        db.execSQL("DROP ${cursor.getString(0)} IF EXISTS ${quoteSqlIdentifier(cursor.getString(1))}")
      }
    }

    database.createSchema(db)
  }
}

private const val PERSISTENT_SCHEMA_OBJECTS = "SELECT upper(type), name FROM sqlite_master " +
    "WHERE type IN ('view', 'table') AND name != 'android_metadata' AND name NOT LIKE 'sqlite%' " +
    "ORDER BY type = 'table'"
