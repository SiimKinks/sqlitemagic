package com.siimkinks.sqlitemagic.framework

import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_BLOB
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.CursorWindow
import android.database.DatabaseUtils
import android.database.DatabaseUtils.STATEMENT_COMMIT
import android.database.SQLException
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteProgram
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement
import android.database.sqlite.SQLiteStatement as FrameworkProgram

internal class FrameworkSQLiteStatement(
  private val db: SQLiteDatabase,
  private val sql: String
) : SQLiteStatement {
  private val columnNames: Array<String> = db
    .rawQuery(sql, NO_ARGUMENTS)
    .use(Cursor::getColumnNames)
  private val bindings = Bindings()
  private val cursorFactory = CursorFactory { _, driver, editTable, query ->
    bindings.bindTo(query)
    SQLiteCursor(driver, editTable, query)
  }
  private var program: FrameworkProgram? = null
  private var cursor: SQLiteCursor? = null
  private lateinit var window: CursorWindow
  private var windowStart = 0
  private var windowEnd = 0
  private var count = 0
  private var position = -1

  override fun bindBlob(index: Int, value: ByteArray) {
    bindings[index] = value
  }

  override fun bindDouble(index: Int, value: Double) {
    bindings[index] = value
  }

  override fun bindLong(index: Int, value: Long) {
    bindings[index] = value
  }

  override fun bindText(index: Int, value: String) {
    bindings[index] = value
  }

  override fun bindNull(index: Int) {
    bindings[index] = null
  }

  override fun getBlob(index: Int): ByteArray = window.getBlob(position, index) ?: EMPTY_BLOB

  override fun getDouble(index: Int) = window.getDouble(position, index)

  override fun getLong(index: Int) = window.getLong(position, index)

  override fun getText(index: Int): String = window.getString(position, index) ?: ""

  override fun isNull(index: Int) = window.getType(position, index) == FIELD_TYPE_NULL

  override fun getColumnCount() = columnNames.size

  override fun getColumnName(index: Int): String = columnNames.getOrElse(index) {
    throw SQLException("Column index $index out of range")
  }

  override fun getColumnType(index: Int) = when (window.getType(position, index)) {
    FIELD_TYPE_INTEGER -> SQLITE_DATA_INTEGER
    FIELD_TYPE_FLOAT -> SQLITE_DATA_FLOAT
    FIELD_TYPE_STRING -> SQLITE_DATA_TEXT
    FIELD_TYPE_BLOB -> SQLITE_DATA_BLOB
    else -> SQLITE_DATA_NULL
  }

  override fun step() = when {
    columnNames.isNotEmpty() -> stepRow()
    else -> {
      execute()
      false
    }
  }

  override fun reset() {
    cursor?.close()
    cursor = null
    count = 0
    position = -1
  }

  override fun clearBindings() = bindings.clear()

  override fun close() {
    reset()
    program?.close()
    program = null
  }

  fun executeInsert() = boundProgram().executeInsert()

  fun executeUpdateDelete() = boundProgram().executeUpdateDelete()

  private fun execute() {
    try {
      boundProgram().execute()
    } catch (failure: Throwable) {
      if (!db.inTransaction() && DatabaseUtils.getSqlStatementType(sql) == STATEMENT_COMMIT) {
        rollbackOutsideSession(failure)
      }
      throw failure
    }
  }

  private fun rollbackOutsideSession(failure: Throwable) {
    try {
      db
        .compileStatement(SESSION_BYPASSING_ROLLBACK)
        .use(FrameworkProgram::execute)
    } catch (rollbackFailure: Throwable) {
      failure.addSuppressed(rollbackFailure)
    }
  }

  private fun boundProgram() = (program ?: db.compileStatement(sql).also { program = it }).also { program ->
    program.clearBindings()
    bindings.bindTo(program)
  }

  private fun openCursor(): SQLiteCursor {
    val opened = db.rawQueryWithFactory(cursorFactory, sql, NO_ARGUMENTS, "") as SQLiteCursor
    try {
      count = opened.count
      window = opened.window
    } catch (failure: Throwable) {
      opened.close()
      throw failure
    }
    windowStart = window.startPosition
    windowEnd = windowStart + window.numRows
    position = -1
    cursor = opened
    return opened
  }

  private fun stepRow(): Boolean {
    val rows = cursor ?: openCursor()
    val next = position + 1
    if (next >= count) {
      position = count
      return false
    }
    if (next < windowStart || next >= windowEnd) {
      if (!rows.onMove(position, next)) {
        return false
      }
      window = rows.window
      windowStart = window.startPosition
      windowEnd = windowStart + window.numRows
    }
    position = next
    return true
  }
}

private class Bindings {
  private var values = arrayOfNulls<Any?>(INITIAL_BINDINGS)
  private var count = 0

  operator fun set(index: Int, value: Any?) {
    if (index > values.size) {
      values = values.copyOf(maxOf(index, values.size * 2))
    }
    values[index - 1] = value
    if (index > count) {
      count = index
    }
  }

  fun clear() {
    values.fill(null)
    count = 0
  }

  fun bindTo(program: SQLiteProgram) {
    for (index in 0 until count) {
      when (val value = values[index]) {
        null -> program.bindNull(index + 1)
        is Long -> program.bindLong(index + 1, value)
        is Double -> program.bindDouble(index + 1, value)
        is String -> program.bindString(index + 1, value)
        else -> program.bindBlob(index + 1, value as ByteArray)
      }
    }
  }
}

private val NO_ARGUMENTS = emptyArray<String>()
private const val SESSION_BYPASSING_ROLLBACK = "/**/ROLLBACK"
private val EMPTY_BLOB = ByteArray(0)
private const val INITIAL_BINDINGS = 8
