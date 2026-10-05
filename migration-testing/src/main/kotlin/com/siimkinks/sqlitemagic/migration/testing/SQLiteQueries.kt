package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

internal data class QueryResult(
  val columns: List<String>,
  val rows: List<List<Any?>>
) {
  fun records() = rows.map { row ->
    columns.zip(row)
      .toMap()
  }
}

internal object SQLiteQueries {
  fun execute(
    connection: SQLiteConnection,
    sql: String,
    bindArgs: List<Any?> = emptyList()
  ) {
    connection
      .prepare(sql)
      .use { statement ->
        bind(
          statement = statement,
          values = bindArgs
        )
        while (statement.step()) {
          // Consume RETURNING rows before releasing the statement.
        }
      }
  }

  fun query(
    connection: SQLiteConnection,
    sql: String,
    bindArgs: List<Any?> = emptyList()
  ): QueryResult = connection
    .prepare(sql)
    .use { statement ->
      bind(
        statement = statement,
        values = bindArgs
      )
      val columns = List(
        size = statement.getColumnCount(),
        init = statement::getColumnName
      )
      QueryResult(
        columns = columns,
        rows = buildList {
          while (statement.step()) {
            add(
              List(size = columns.size) { index ->
                read(
                  statement = statement,
                  index = index
                )
              }
            )
          }
        }
      )
    }

  private fun bind(
    statement: SQLiteStatement,
    values: List<Any?>
  ) = values.forEachIndexed { index, value ->
    val parameter = index + 1
    when (value) {
      null -> statement.bindNull(parameter)
      is String -> statement.bindText(
        index = parameter,
        value = value
      )
      is ByteArray -> statement.bindBlob(
        index = parameter,
        value = value
      )
      is Boolean -> statement.bindLong(
        index = parameter,
        value = when {
          value -> 1L
          else -> 0L
        }
      )
      is Byte, is Short, is Int, is Long -> statement.bindLong(
        index = parameter,
        value = (value as Number).toLong()
      )
      is Float, is Double -> statement.bindDouble(
        index = parameter,
        value = (value as Number).toDouble()
      )
      else -> error("Unsupported bound parameter $parameter: ${value.javaClass.name}")
    }
  }

  private fun read(
    statement: SQLiteStatement,
    index: Int
  ): Any? = when (statement.getColumnType(index)) {
    1 -> statement.getLong(index)
    2 -> statement.getDouble(index)
    3 -> statement.getText(index)
    4 -> statement.getBlob(index)
    5 -> null
    else -> error("Unsupported SQLite result type at column $index")
  }
}
