package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.GeneratedViewResolver
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.LogUtil.loggingFailure

@InternalSqliteMagicApi
object SqlUtil {
  fun createSchema(
    connection: SQLiteConnection,
    schema: GeneratedSchema,
    temporary: Boolean
  ) = inTransaction(connection) {
    logDebug {
      when {
        temporary -> "Creating temporary tables"
        else -> "Creating tables"
      }
    }
    schema.createSchemaTables(
      connection = connection,
      temporary = temporary
    )
    createSchemaViews(
      connection = connection,
      schema = schema,
      temporary = temporary,
      message = "Creating views"
    )
    logDebug { "Creating indexes" }
    schema.createSchemaIndexes(
      connection = connection,
      temporary = temporary
    )
  }

  fun migrateViews(
    connection: SQLiteConnection,
    schema: GeneratedSchema
  ) = loggingFailure(message = "Error while migrating views") {
    createSchemaViews(
      connection = connection,
      schema = schema,
      temporary = false,
      message = "Migrating views"
    )
  }

  fun clearData(
    connection: SQLiteConnection,
    schema: GeneratedSchema,
    tableCount: Int
  ) = inTransaction(connection) {
    logDebug { "Clearing data" }
    MutableScatterSet<String>(tableCount).also {
      schema.clearTables(
        connection = connection,
        changedTables = it
      )
    }
  }

  internal fun createViews(
    connection: SQLiteConnection,
    views: Collection<GeneratedView>,
    temporary: Boolean
  ) = GeneratedViewResolver(views)
    .resolve(temporary = temporary)
    .forEach {
      createViewSql(
        connection = connection,
        view = it
      )
    }

  private fun createSchemaViews(
    connection: SQLiteConnection,
    schema: GeneratedSchema,
    temporary: Boolean,
    message: String
  ) {
    val views = buildList { schema.collectGeneratedViews(this) }
    if (views.isNotEmpty()) {
      logDebug { message }
      createViews(
        connection = connection,
        views = views,
        temporary = temporary
      )
    }
  }

  private inline fun <T> inTransaction(
    connection: SQLiteConnection,
    block: () -> T
  ) = loggingFailure(message = "Error while executing db transaction") {
    when {
      connection.inTransaction() -> block()
      else -> {
        connection.execSQL("BEGIN IMMEDIATE")
        try {
          block().also { connection.execSQL("COMMIT") }
        } catch (failure: Throwable) {
          if (connection.inTransaction()) {
            try {
              connection.execSQL("ROLLBACK")
            } catch (rollbackFailure: Throwable) {
              failure.addSuppressed(rollbackFailure)
            }
          }
          throw failure
        }
      }
    }
  }

  private fun createViewSql(connection: SQLiteConnection, view: GeneratedView) {
    val create = when {
      view.temporary -> "CREATE TEMPORARY VIEW"
      else -> "CREATE VIEW"
    }
    connection.execSQL("$create IF NOT EXISTS ${quoteSqlIdentifier(view.viewName)} AS ${view.definition.sql}")
  }

  internal fun quoteSqlIdentifier(name: String) = buildString(capacity = name.length + 2) {
    appendQuotedSqlIdentifier(name)
  }

  /**
   * Require that the statement of a migration script line does not control a transaction or savepoint. Migration
   * scripts run inside the upgrade's transaction.
   *
   * @param sql Migration script line
   * @param start Index of the statement's first token; by default the first one after whitespace and block comments
   * @throws IllegalArgumentException if the statement begins with `BEGIN`, `COMMIT`, `END`, `ROLLBACK`, `SAVEPOINT` or
   *     `RELEASE`
   */
  fun requireNoTransactionControl(
    sql: String,
    start: Int = sql.firstTokenStart()
  ) {
    val keyword = sql
      .substring(start)
      .takeWhile(Char::isIdentifierPart)
    val transactionControl = keyword.all { it.code < 128 } && TRANSACTION_CONTROL.any { control ->
      control.equals(
        other = keyword,
        ignoreCase = true
      )
    }
    require(!transactionControl) {
      "Unsupported migration transaction control '$keyword': the migration caller owns the transaction"
    }
  }
}

internal fun StringBuilder.appendQuotedSqlIdentifier(name: String) = apply {
  append('"')
  when {
    name.indexOf('"') < 0 -> append(name)
    else -> for (character in name) {
      if (character == '"') {
        append('"')
      }
      append(character)
    }
  }
  append('"')
}

private tailrec fun String.firstTokenStart(index: Int = 0): Int = when {
  index >= length -> length
  this[index].isWhitespace() || this[index] == '\uFEFF' -> firstTokenStart(index + 1)
  startsWith(
    prefix = "/*",
    startIndex = index
  ) -> {
    val commentEnd = indexOf(
      string = "*/",
      startIndex = index + 2
    )
    when (commentEnd) {
      -1 -> length
      else -> firstTokenStart(commentEnd + 2)
    }
  }
  else -> index
}

private fun Char.isIdentifierPart() = isLetterOrDigit() || this == '_' || this == '$' || code >= 128

private val TRANSACTION_CONTROL = arrayOf("BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT", "RELEASE")
