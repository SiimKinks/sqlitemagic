package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.GeneratedViewResolver
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.LogUtil.loggingFailure

@InternalSqliteMagicApi
object SqlUtil {
  fun createSchema(
    db: SupportSQLiteDatabase,
    schema: GeneratedSchema,
    temporary: Boolean
  ) = inTransaction(db) {
    logDebug {
      when {
        temporary -> "Creating temporary tables"
        else -> "Creating tables"
      }
    }
    schema.createSchemaTables(
      db = db,
      temporary = temporary
    )
    createSchemaViews(
      db = db,
      schema = schema,
      temporary = temporary,
      message = "Creating views"
    )
    logDebug { "Creating indexes" }
    schema.createSchemaIndexes(
      db = db,
      temporary = temporary
    )
  }

  fun migrateViews(
    db: SupportSQLiteDatabase,
    schema: GeneratedSchema
  ) = loggingFailure(message = "Error while migrating views") {
    createSchemaViews(
      db = db,
      schema = schema,
      temporary = false,
      message = "Migrating views"
    )
  }

  fun clearData(
    db: SupportSQLiteDatabase,
    schema: GeneratedSchema,
    tableCount: Int
  ) = inTransaction(db) {
    logDebug { "Clearing data" }
    MutableScatterSet<String>(tableCount).also {
      schema.clearTables(
        db = db,
        changedTables = it
      )
    }
  }

  internal fun createViews(
    db: SupportSQLiteDatabase,
    views: Collection<GeneratedView>,
    temporary: Boolean
  ) = GeneratedViewResolver(views)
    .resolve(temporary = temporary)
    .forEach {
      createViewSql(
        db = db,
        view = it
      )
    }

  private fun createSchemaViews(
    db: SupportSQLiteDatabase,
    schema: GeneratedSchema,
    temporary: Boolean,
    message: String
  ) {
    val views = buildList { schema.collectGeneratedViews(this) }
    if (views.isNotEmpty()) {
      logDebug { message }
      createViews(
        db = db,
        views = views,
        temporary = temporary
      )
    }
  }

  private inline fun <T> inTransaction(
    db: SupportSQLiteDatabase,
    block: () -> T
  ) = loggingFailure(message = "Error while executing db transaction") {
    db.beginTransaction()
    try {
      block().also { db.setTransactionSuccessful() }
    } finally {
      db.endTransaction()
    }
  }

  private fun createViewSql(db: SupportSQLiteDatabase, view: GeneratedView) {
    val create = when {
      view.temporary -> "CREATE TEMPORARY VIEW"
      else -> "CREATE VIEW"
    }
    db.execSQL("$create IF NOT EXISTS ${quoteSqlIdentifier(view.viewName)} AS ${view.definition.sql}")
  }

  internal fun quoteSqlIdentifier(name: String) =
    """"${name.replace(oldValue = "\"", newValue = "\"\"")}""""
}
