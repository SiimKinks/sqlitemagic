package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema

internal fun SQLiteConnection.createPersistentViews(views: List<MigrationViewSchema>) {
  views.forEach { view ->
    try {
      execSQL(view.createSql)
    } catch (exception: Exception) {
      throw IllegalStateException(
        "Error creating target persistent view ${view.name}: ${view.createSql.take(500)}",
        exception
      )
    }
  }
}
