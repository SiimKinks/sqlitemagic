package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase

internal class DefaultDbDowngrader(
  private val database: GeneratedDatabase
) : DbDowngrader {
  override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logDebug("Downgrading database from $oldVersion to $newVersion")
    }
    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name != 'android_metadata' AND name NOT LIKE 'sqlite%'")
      .use { cursor ->
        while (cursor.moveToNext()) {
          val tableName = cursor.getString(0)
          db.execSQL("DROP TABLE IF EXISTS $tableName")
        }
      }

    database.createSchema(db)
  }
}
