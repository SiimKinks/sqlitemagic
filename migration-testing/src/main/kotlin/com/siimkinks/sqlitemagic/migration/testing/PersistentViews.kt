package com.siimkinks.sqlitemagic.migration.testing

import com.siimkinks.sqlitemagic.migration.MigrationDatabase
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema

internal fun MigrationDatabase.createPersistentViews(views: List<MigrationViewSchema>) {
  views.forEach { view ->
    try {
      execute(view.createSql)
    } catch (exception: Exception) {
      throw IllegalStateException(
        "Error creating target persistent view ${view.name}: ${view.createSql.take(500)}",
        exception
      )
    }
  }
}
