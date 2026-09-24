package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.sqlite.SQLiteDoneException
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.Utils.ValueParser

/** Conversion behavior supplied by generated columns to the runtime. */
class ColumnValueAdapter<T> private constructor(
  private val serialize: (T & Any) -> String,
  private val readCursor: (Cursor, Boolean) -> Any?,
  private val readStatement: (SupportSQLiteStatement) -> Any?
) {
  internal fun toSqlArg(value: T & Any) = serialize(value)

  @Suppress("UNCHECKED_CAST")
  internal fun <V> getFromCursor(cursor: Cursor, nullable: Boolean): V? = readCursor(cursor, nullable) as V?

  @Suppress("UNCHECKED_CAST")
  internal fun <V> getFromStatement(statement: SupportSQLiteStatement): V? = readStatement(statement) as V?

  companion object {
    /** Use when the database reader accepts only a non-null storage value. */
    fun <T, S> transformed(
      parser: ValueParser<S>,
      toDb: (T & Any) -> S?,
      fromDb: (S) -> T?
    ): ColumnValueAdapter<T> = ColumnValueAdapter(
      serialize = { value -> sqlArg(toDb(value)) },
      readCursor = { cursor, nullable ->
        parseFromCursor(
          parser = parser,
          cursor = cursor,
          nullable = nullable
        )?.let(fromDb)
      },
      readStatement = { statement ->
        parseFromStatementOrNull(
          parser = parser,
          statement = statement
        )?.let(fromDb)
      }
    )

    /** Use when the database reader must also receive null or an empty statement result. */
    fun <T, S> transformedNullableInput(
      parser: ValueParser<S>,
      toDb: (T & Any) -> S?,
      fromDb: (S?) -> T?
    ): ColumnValueAdapter<T> = ColumnValueAdapter(
      serialize = { value -> sqlArg(toDb(value)) },
      readCursor = { cursor, nullable ->
        fromDb(
          parseFromCursor(
            parser = parser,
            cursor = cursor,
            nullable = nullable
          )
        )
      },
      readStatement = { statement ->
        fromDb(
          parseFromStatementOrNull(
            parser = parser,
            statement = statement
          )
        )
      }
    )

    /** Use for relationship columns that serialize an ID but read its storage type directly. */
    fun <T, S> serializing(
      parser: ValueParser<S>,
      toDb: (T & Any) -> S?
    ): ColumnValueAdapter<T> = ColumnValueAdapter(
      serialize = { value -> sqlArg(toDb(value)) },
      readCursor = { cursor, nullable ->
        parseFromCursor(
          parser = parser,
          cursor = cursor,
          nullable = nullable
        )
      },
      readStatement = { statement ->
        parseFromStatementOrNull(
          parser = parser,
          statement = statement
        )
      }
    )

    private fun sqlArg(value: Any?) = value
      ?.toString()
      ?: throw NullPointerException("SQL argument cannot be null")

    private fun <S> parseFromCursor(
      parser: ValueParser<S>,
      cursor: Cursor,
      nullable: Boolean
    ): S? = when {
      nullable && cursor.isNull(0) -> null
      else -> parser.parseFromCursor(cursor)
    }

    private fun <S> parseFromStatementOrNull(
      parser: ValueParser<S>,
      statement: SupportSQLiteStatement
    ): S? = try {
      parser.parseFromStatement(statement)
    } catch (_: SQLiteDoneException) {
      null
    }
  }
}
