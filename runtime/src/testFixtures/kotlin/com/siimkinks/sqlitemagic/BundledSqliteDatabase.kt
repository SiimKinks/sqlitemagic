package com.siimkinks.sqlitemagic

import android.content.ContentResolver
import android.content.ContentValues
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_BLOB
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.DataSetObserver
import android.database.sqlite.SQLiteTransactionListener
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.SqlUtil
import java.util.Locale

internal class BundledSqliteDatabase : SupportSQLiteDatabase {
  private val connection = BundledSQLiteDriver().open(":memory:")
  private val transactions = ArrayDeque<BundledTransaction>()
  override var isOpen = true
    private set

  fun dbConnection(
    adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = MutableScatterMap(initialCapacity = 0)
  ) = DbConnectionImpl(
    database = TestGeneratedDatabase(tableCount = 1),
    dbHelper = object : SupportSQLiteOpenHelper {
      override val databaseName: String? = null
      override val writableDatabase = this@BundledSqliteDatabase
      override val readableDatabase = this@BundledSqliteDatabase

      override fun setWriteAheadLoggingEnabled(enabled: Boolean) = unsupported()

      override fun close() = this@BundledSqliteDatabase.close()
    },
    adapterSettings = adapterSettings
  )

  fun createViews(vararg views: GeneratedView) = SqlUtil.createViews(
    db = this,
    views = views.asList(),
    temporary = false
  )

  override fun execSQL(sql: String) = connection.execSQL(sql)

  override fun execSQL(
    sql: String,
    bindArgs: Array<out Any?>
  ) = connection
    .prepare(sql)
    .use { statement ->
      SimpleSQLiteQuery(
        query = sql,
        bindArgs = bindArgs
      ).bindTo(BundledProgram(statement))
      statement.step()
      Unit
    }

  override fun query(query: String) = query(SimpleSQLiteQuery(query))

  override fun query(
    query: String,
    bindArgs: Array<out Any?>
  ) = query(
    SimpleSQLiteQuery(
      query = query,
      bindArgs = bindArgs
    )
  )

  override fun query(query: SupportSQLiteQuery): Cursor = connection
    .prepare(query.sql)
    .use { statement ->
      query.bindTo(BundledProgram(statement))
      MaterializedCursor.read(statement)
    }

  override fun query(
    query: SupportSQLiteQuery,
    cancellationSignal: CancellationSignal?
  ) = query(query)

  private fun begin(listener: SQLiteTransactionListener?) {
    if (transactions.isEmpty()) {
      connection.execSQL("BEGIN IMMEDIATE")
    }
    transactions.addLast(BundledTransaction(listener))
    listener?.onBegin()
  }

  override fun close() {
    if (isOpen) {
      isOpen = false
      connection.close()
    }
  }

  override fun compileStatement(sql: String): SupportSQLiteStatement = BundledStatement(
    connection = connection,
    statement = connection.prepare(sql)
  )

  override fun beginTransaction() = begin(listener = null)

  override fun beginTransactionNonExclusive() = begin(listener = null)

  override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) = begin(
    listener = transactionListener
  )

  override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) = begin(
    listener = transactionListener
  )

  override fun endTransaction() {
    val transaction = transactions.removeLast()
    val successful = transaction.successful && !transaction.childFailed
    transaction.listener?.let { listener ->
      when {
        successful -> listener.onCommit()
        else -> listener.onRollback()
      }
    }
    when (val parent = transactions.lastOrNull()) {
      null -> connection.execSQL(
        when {
          successful -> "COMMIT"
          else -> "ROLLBACK"
        }
      )
      else -> if (!successful) parent.childFailed = true
    }
  }

  override fun setTransactionSuccessful() {
    transactions.last().successful = true
  }

  override fun inTransaction() = connection.inTransaction()

  override val isDbLockedByCurrentThread get() = unsupported()

  override fun yieldIfContendedSafely() = unsupported()

  override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long) = unsupported()

  override var version: Int
    get() = unsupported()
    set(value) = unsupported()

  override val maximumSize get() = unsupported()

  override fun setMaximumSize(numBytes: Long) = unsupported()

  override var pageSize: Long
    get() = unsupported()
    set(value) = unsupported()

  override fun insert(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues
  ) = unsupported()

  override fun delete(
    table: String,
    whereClause: String?,
    whereArgs: Array<out Any?>?
  ) = unsupported()

  override fun update(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues,
    whereClause: String?,
    whereArgs: Array<out Any?>?
  ) = unsupported()

  override val isReadOnly = false

  override fun needUpgrade(newVersion: Int) = unsupported()

  override val path: String? = null

  override fun setLocale(locale: Locale) = unsupported()

  override fun setMaxSqlCacheSize(cacheSize: Int) = unsupported()

  override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = unsupported()

  override fun enableWriteAheadLogging() = unsupported()

  override fun disableWriteAheadLogging() = unsupported()

  override val isWriteAheadLoggingEnabled = false

  override val attachedDbs get() = unsupported()

  override val isDatabaseIntegrityOk get() = unsupported()
}

private class BundledProgram(private val statement: SQLiteStatement) : SupportSQLiteProgram {
  override fun bindNull(index: Int) = statement.bindNull(index)

  override fun bindLong(index: Int, value: Long) = statement.bindLong(
    index = index,
    value = value
  )

  override fun bindDouble(index: Int, value: Double) = statement.bindDouble(
    index = index,
    value = value
  )

  override fun bindString(index: Int, value: String) = statement.bindText(
    index = index,
    value = value
  )

  override fun bindBlob(index: Int, value: ByteArray) = statement.bindBlob(
    index = index,
    value = value
  )

  override fun clearBindings() = statement.clearBindings()

  override fun close() = Unit
}

private class BundledTransaction(val listener: SQLiteTransactionListener?) {
  var successful = false
  var childFailed = false
}

private class BundledStatement(
  private val connection: SQLiteConnection,
  private val statement: SQLiteStatement
) : SupportSQLiteStatement, SupportSQLiteProgram by BundledProgram(statement) {
  override fun execute() {
    statement.reset()
    statement.step()
  }

  override fun executeUpdateDelete(): Int {
    execute()
    return connection
      .prepare("SELECT changes()")
      .use { changes ->
        changes.step()
        changes.getLong(0).toInt()
      }
  }

  override fun executeInsert() = unsupported()

  override fun simpleQueryForLong() = unsupported()

  override fun simpleQueryForString() = unsupported()

  override fun close() = statement.close()
}

private class MaterializedCursor(
  private val columnNames: Array<String>,
  private val rows: List<Array<Any?>>
) : Cursor {
  private var position = -1
  private var closed = false

  override fun getCount() = rows.size

  override fun getPosition() = position

  override fun move(offset: Int) = moveToPosition(position + offset)

  override fun moveToPosition(position: Int): Boolean {
    this.position = position.coerceIn(
      minimumValue = -1,
      maximumValue = rows.size
    )
    return this.position == position && position in rows.indices
  }

  override fun moveToFirst() = moveToPosition(0)

  override fun moveToLast() = moveToPosition(rows.lastIndex)

  override fun moveToNext() = moveToPosition(position + 1)

  override fun moveToPrevious() = moveToPosition(position - 1)

  override fun isFirst() = rows.isNotEmpty() && position == 0

  override fun isLast() = rows.isNotEmpty() && position == rows.lastIndex

  override fun isBeforeFirst() = rows.isEmpty() || position == -1

  override fun isAfterLast() = rows.isEmpty() || position == rows.size

  override fun getColumnCount() = columnNames.size

  override fun getColumnIndex(columnName: String) = columnNames.indexOf(columnName)

  override fun getColumnIndexOrThrow(columnName: String) = getColumnIndex(columnName).also { index ->
    require(index != -1) { "Column '$columnName' does not exist" }
  }

  override fun getColumnName(columnIndex: Int) = columnNames[columnIndex]

  override fun getColumnNames() = columnNames

  override fun getType(columnIndex: Int) = when (value(columnIndex)) {
    null -> FIELD_TYPE_NULL
    is Long -> FIELD_TYPE_INTEGER
    is Double -> FIELD_TYPE_FLOAT
    is String -> FIELD_TYPE_STRING
    else -> FIELD_TYPE_BLOB
  }

  override fun isNull(columnIndex: Int) = value(columnIndex) == null

  override fun getLong(columnIndex: Int) = when (val value = value(columnIndex)) {
    null -> 0L
    is Long -> value
    is Double -> value.toLong()
    is String -> value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong() ?: 0L
    else -> unconvertible(target = "long")
  }

  override fun getInt(columnIndex: Int) = getLong(columnIndex).toInt()

  override fun getShort(columnIndex: Int) = getLong(columnIndex).toInt().toShort()

  override fun getDouble(columnIndex: Int) = when (val value = value(columnIndex)) {
    null -> 0.0
    is Long -> value.toDouble()
    is Double -> value
    is String -> value.toDoubleOrNull() ?: 0.0
    else -> unconvertible(target = "double")
  }

  override fun getFloat(columnIndex: Int) = getDouble(columnIndex).toFloat()

  override fun getString(columnIndex: Int) = when (val value = value(columnIndex)) {
    null -> null
    is Long, is Double, is String -> value.toString()
    else -> unconvertible(target = "string")
  }

  override fun getBlob(columnIndex: Int) = when (val value = value(columnIndex)) {
    null -> null
    is ByteArray -> value
    is String -> value.toByteArray(Charsets.UTF_8)
    else -> error("Unable to convert a numeric value to blob")
  }

  override fun close() {
    closed = true
  }

  override fun isClosed() = closed

  override fun getWantsAllOnMoveCalls() = false

  private fun value(columnIndex: Int): Any? {
    check(!closed) { "Cursor is closed" }
    check(position in rows.indices) { "Cursor position $position is outside ${rows.size} rows" }
    return rows[position][columnIndex]
  }

  private fun unconvertible(target: String): Nothing = error("Unable to convert BLOB to $target")

  override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer) = unsupported()

  @Deprecated("Deprecated in Cursor")
  override fun deactivate() = unsupported()

  @Deprecated("Deprecated in Cursor")
  override fun requery() = unsupported()

  override fun registerContentObserver(observer: ContentObserver) = unsupported()

  override fun unregisterContentObserver(observer: ContentObserver) = unsupported()

  override fun registerDataSetObserver(observer: DataSetObserver) = unsupported()

  override fun unregisterDataSetObserver(observer: DataSetObserver) = unsupported()

  override fun setNotificationUri(cr: ContentResolver, uri: Uri) = unsupported()

  override fun getNotificationUri() = unsupported()

  override fun setExtras(extras: Bundle) = unsupported()

  override fun getExtras() = unsupported()

  override fun respond(extras: Bundle) = unsupported()

  companion object {
    fun read(statement: SQLiteStatement): Cursor {
      val columnCount = statement.getColumnCount()
      val columnNames = Array(
        size = columnCount,
        init = statement::getColumnName
      )
      val rows = buildList {
        while (statement.step()) {
          add(
            Array<Any?>(columnCount) { index ->
              when (statement.getColumnType(index)) {
                SQLITE_DATA_INTEGER -> statement.getLong(index)
                SQLITE_DATA_FLOAT -> statement.getDouble(index)
                SQLITE_DATA_TEXT -> statement.getText(index)
                SQLITE_DATA_BLOB -> statement.getBlob(index)
                else -> null
              }
            }
          )
        }
      }
      return MaterializedCursor(
        columnNames = columnNames,
        rows = rows
      )
    }
  }
}

private fun unsupported(): Nothing = throw UnsupportedOperationException("Not supported by the JVM test database")
