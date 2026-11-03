package com.siimkinks.sqlitemagic.framework

import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_BLOB
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.CursorWindow
import android.database.SQLException
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteProgram
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.NullableValueReads
import android.database.sqlite.SQLiteStatement as FrameworkProgram

internal class FrameworkSQLiteStatement(
  private val db: SQLiteDatabase,
  private val sql: String,
  private val idleWindow: IdleCursorWindow,
  private val readSnapshot: ReadSnapshot,
  private val transaction: TransactionState
) : SQLiteStatement, NullableValueReads {
  private var columnNames = preparedColumnNames()
  private val schemaDependentColumns = hasSchemaDependentColumns(sql)
  private val control = when {
    columnNames.isEmpty() -> transactionControlOf(sql)
    else -> TransactionControl.NONE
  }
  private val program = when {
    columnNames.isEmpty() -> db.compileStatement(
      sessionBypassing(
        sql = sql,
        control = control
      )
    )
    else -> null
  }
  private val bindings = Bindings()
  private val cursorFactory = CursorFactory { _, driver, editTable, query ->
    bindings.bindTo(query)
    SQLiteCursor(driver, editTable, query).apply {
      if (SDK_INT >= P) {
        setFillWindowForwardOnly(true)
      }
      idleWindow
        .take()
        ?.let(::setWindow)
    }
  }
  private var rowMutationProgram: FrameworkProgram? = null
  private var cursor: SQLiteCursor? = null
  private lateinit var window: CursorWindow
  private var windowStart = 0
  private var windowEnd = 0
  private var count = 0
  private var position = -1
  private var holdsSnapshot = false
  private var readsLargeResult = false

  override fun bindBlob(index: Int, value: ByteArray) {
    when (val compiled = program) {
      null -> bindings[index] = value
      else -> compiled.bindBlob(index, value)
    }
  }

  override fun bindDouble(index: Int, value: Double) {
    when (val compiled = program) {
      null -> bindings[index] = value
      else -> compiled.bindDouble(index, value)
    }
  }

  override fun bindLong(index: Int, value: Long) {
    when (val compiled = program) {
      null -> bindings[index] = value
      else -> compiled.bindLong(index, value)
    }
  }

  override fun bindText(index: Int, value: String) {
    when (val compiled = program) {
      null -> bindings[index] = value
      else -> compiled.bindString(index, value)
    }
  }

  override fun bindNull(index: Int) {
    when (val compiled = program) {
      null -> bindings[index] = null
      else -> compiled.bindNull(index)
    }
  }

  override fun getBlob(index: Int) = getBlobOrNull(index) ?: EMPTY_BLOB

  override fun getBlobOrNull(index: Int): ByteArray? = window.getBlob(position, index)

  override fun getDouble(index: Int) = window.getDouble(position, index)

  override fun getLong(index: Int) = window.getLong(position, index)

  override fun getText(index: Int) = getTextOrNull(index) ?: ""

  override fun getTextOrNull(index: Int): String? = window.getString(position, index)

  override fun isNull(index: Int) = window.getType(position, index) == FIELD_TYPE_NULL

  override fun getColumnCount() = columnNames.size

  override fun getColumnName(index: Int) = columnNames.getOrElse(index) {
    throw SQLException("Column index $index out of range")
  }

  override fun getColumnType(index: Int) = when (window.getType(position, index)) {
    FIELD_TYPE_INTEGER -> SQLITE_DATA_INTEGER
    FIELD_TYPE_FLOAT -> SQLITE_DATA_FLOAT
    FIELD_TYPE_STRING -> SQLITE_DATA_TEXT
    FIELD_TYPE_BLOB -> SQLITE_DATA_BLOB
    else -> SQLITE_DATA_NULL
  }

  override fun step() = when (val compiled = program) {
    null -> transaction.guard { stepRow() }
    else -> {
      transaction.execute(
        program = compiled,
        control = control
      )
      false
    }
  }

  override fun reset() {
    cursor?.let(::closeKeepingWindow)
    cursor = null
    count = 0
    position = -1
    endSnapshot()
  }

  override fun clearBindings() = when (val compiled = program) {
    null -> bindings.clear()
    else -> compiled.clearBindings()
  }

  override fun close() {
    reset()
    program?.close()
    rowMutationProgram?.close()
    rowMutationProgram = null
  }

  fun executeInsert() = transaction.guard { mutationProgram().executeInsert() }

  fun executeUpdateDelete() = transaction.guard { mutationProgram().executeUpdateDelete() }

  private fun closeKeepingWindow(opened: SQLiteCursor) {
    val window = opened.window
    window?.acquireReference()
    opened.close()
    window?.let(idleWindow::release)
  }

  private fun mutationProgram() = program ?: boundRowMutationProgram()

  private fun boundRowMutationProgram() = (rowMutationProgram ?: db
    .compileStatement(sql)
    .also { rowMutationProgram = it })
    .also { compiled ->
      compiled.clearBindings()
      bindings.bindTo(compiled)
    }

  private fun openRows(): SQLiteCursor {
    if (readsLargeResult) {
      holdsSnapshot = readSnapshot.join() || readSnapshot.begin()
    }
    val opened = openCursor().also { cursor = it }
    return when {
      count <= windowEnd -> {
        readsLargeResult = false
        endSnapshot()
        opened
      }
      holdsSnapshot -> opened
      readSnapshot.join() -> {
        holdsSnapshot = true
        opened
      }
      readSnapshot.begin() -> {
        holdsSnapshot = true
        readsLargeResult = true
        closeKeepingWindow(opened)
        cursor = null
        openCursor().also { cursor = it }
      }
      else -> opened
    }
  }

  private fun openCursor(): SQLiteCursor {
    val opened = db.rawQueryWithFactory(cursorFactory, sql, NO_ARGUMENTS, "") as SQLiteCursor
    val refill = try {
      count = opened.count
      useWindow(opened.window)
      schemaDependentColumns && filledWithOldColumns(opened)
    } catch (failure: Throwable) {
      opened.close()
      throw failure
    }
    if (refill) {
      closeKeepingWindow(opened)
      return openCursor()
    }
    position = -1
    return opened
  }

  private fun filledWithOldColumns(opened: SQLiteCursor): Boolean {
    columnNames = preparedColumnNames()
    return !columnNames.contentEquals(opened.columnNames)
  }

  private fun preparedColumnNames(): Array<String> = db
    .rawQuery(sql, NO_ARGUMENTS)
    .use(Cursor::getColumnNames)

  private fun useWindow(current: CursorWindow) {
    window = current
    windowStart = current.startPosition
    windowEnd = windowStart + current.numRows
  }

  private fun endSnapshot() {
    if (holdsSnapshot) {
      holdsSnapshot = false
      readSnapshot.end()
    }
  }

  private fun stepRow(): Boolean {
    val rows = cursor ?: openRows()
    val next = position + 1
    if (next >= count) {
      position = count
      endSnapshot()
      return false
    }
    if (next !in windowStart..<windowEnd) {
      if (!rows.onMove(position, next)) {
        return false
      }
      useWindow(rows.window)
      if (next !in windowStart..<windowEnd) {
        throw SQLiteException(
          "Couldn't read row $next from CursorWindow: the row is larger than the window or no longer in the result"
        )
      }
    }
    position = next
    return true
  }
}

private class Bindings {
  private var values = arrayOfNulls<Any>(INITIAL_BINDINGS)
  private var count = 0

  operator fun set(index: Int, value: Any?) {
    if (index > values.size) {
      values = values.copyOf(
        maxOf(
          a = index,
          b = values.size * 2
        )
      )
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
    for (index in 0..<count) {
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
private val EMPTY_BLOB = ByteArray(0)
private const val INITIAL_BINDINGS = 8
