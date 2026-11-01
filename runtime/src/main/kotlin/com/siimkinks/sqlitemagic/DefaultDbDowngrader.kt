package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

internal class DefaultDbDowngrader(
  private val database: GeneratedDatabase
) : DbDowngrader {
  override fun onDowngrade(connection: SQLiteConnection, oldVersion: Int, newVersion: Int) {
    logDebug { "Downgrading database from $oldVersion to $newVersion" }
    val drops = connection
      .prepare(PERSISTENT_SCHEMA_OBJECTS)
      .use { statement ->
        buildList {
          while (statement.step()) {
            add("DROP ${statement.getText(0)} IF EXISTS ${quoteSqlIdentifier(statement.getText(1))}")
          }
        }
      }
    drops.forEach(connection::execSQL)
    database.createSchema(connection)
  }
}

private const val PERSISTENT_SCHEMA_OBJECTS = "SELECT upper(type), name FROM sqlite_master " +
    "WHERE type IN ('view', 'table') AND name != 'android_metadata' AND name NOT LIKE 'sqlite%' " +
    "ORDER BY type = 'table'"
