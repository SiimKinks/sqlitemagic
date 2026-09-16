package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.CompiledSelectImpl.Companion.createQueryObservable
import com.siimkinks.sqlitemagic.CompiledSelectImpl.CompiledCountSelectImpl
import com.siimkinks.sqlitemagic.CompiledSelectImpl.CompiledFirstSelectImpl.Companion.addTakeFirstLimitClauseIfNeeded
import com.siimkinks.sqlitemagic.Query.DatabaseQuery
import java.util.concurrent.TimeUnit.NANOSECONDS

@Suppress("UNCHECKED_CAST")
internal class CompiledSelect1Impl<T, S>(
  @JvmField
  val sql: String,
  @JvmField
  val args: Array<String>?,
  dbConnection: DbConnectionImpl,
  @JvmField
  val selectedColumn: Column<*, T, *, *, *>,
  @JvmField
  val observedTables: Array<String>
) : DatabaseQuery<List<T>, T>(
  dbConnection = dbConnection,
  mapper = Mapper { cursor ->
    selectedColumn.getFromCursor<T>(cursor) as T
  }
), CompiledSelect<T, S> {
  override fun rawQuery(inStream: Boolean): Cursor {
    super.rawQuery(inStream)
    val db = dbConnection.readableDatabase
    val startNanos = System.nanoTime()
    val cursor = SqlUtil.query(db, sql, args)
    if (SqliteMagic.LOGGING_ENABLED) {
      val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
    }
    return FastCursor.tryCreate(cursor)
  }

  override fun map(cursor: Cursor?): List<T> {
    checkNotNull(cursor).use { cursor ->
      val rowCount = cursor.count
      if (rowCount == 0) {
        return emptyList()
      }
      val selectedColumn = selectedColumn
      return buildList(rowCount) {
        while (cursor.moveToNext()) {
          add(selectedColumn.getFromCursor<T>(cursor) as T)
        }
      }
    }
  }

  override fun toString() = "[Select1<List>; sql=$sql]"

  override fun execute() = map(rawQuery(false))

  override fun observe() = ListQueryObservable(
    createQueryObservable(
      observedTables = observedTables,
      query = this
    )
  )

  override fun takeFirst() = CompiledFirstSelect1Impl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  override fun count(): CompiledCountSelect<S> = CompiledCountSelectImpl(
    parentSql = sql,
    args = args,
    dbConnection = dbConnection,
    observedTables = observedTables
  )

  override fun toCursor() = CompiledCursorSelect1Impl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  internal class CompiledFirstSelect1Impl<T, S>(
    compiledSelect: CompiledSelect1Impl<T, S>,
    dbConnection: DbConnectionImpl
  ) : DatabaseQuery<T, T>(dbConnection, null), CompiledFirstSelect<T, S> {
    private val selectStm: SupportSQLiteStatement?

    @JvmField
    val sql: String

    @JvmField
    val args: Array<String>?

    @JvmField
    val selectedColumn = compiledSelect.selectedColumn

    @JvmField
    val observedTables = compiledSelect.observedTables

    init {
      val sql = addTakeFirstLimitClauseIfNeeded(compiledSelect.sql)
      val args = compiledSelect.args
      val selectStm = when {
        compiledSelect.selectedColumn.valueParser.supportsStatementParsing() -> dbConnection
          .compileStatement(sql)
          .also { statement ->
            SqlUtil.bindAllArgsAsStrings(statement, args)
          }
        else -> null
      }
      this.selectStm = selectStm
      this.sql = sql
      this.args = args
    }

    override fun map(cursor: Cursor?) = execute()

    override fun execute(): T? {
      val selectStm = selectStm ?: return executeFromCursor()
      val result: T?
      val startNanos: Long
      synchronized(selectStm) {
        startNanos = System.nanoTime()
        result = selectedColumn.getFromStatement(selectStm)
      }
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return result
    }

    private fun executeFromCursor(): T? {
      val startNanos = System.nanoTime()
      val cursor = FastCursor.tryCreate(
        SqlUtil.query(dbConnection.readableDatabase, sql, args)
      )
      val result = cursor.use {
        when {
          it.moveToNext() -> selectedColumn.getFromCursor<T>(it)
          else -> null
        }
      }
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return result
    }

    override fun observe() = SingleItemQueryObservable(
      createQueryObservable(
        observedTables = observedTables,
        query = this
      )
    )

    override fun toString() = "[TAKE FIRST 1;sql=$sql]"
  }

  internal class CompiledCursorSelect1Impl<T, S>(
    compiledSelect: CompiledSelect1Impl<T, S>,
    dbConnection: DbConnectionImpl
  ) : DatabaseQuery<Cursor, T>(dbConnection, null), CompiledCursorSelect<T, S> {
    @JvmField
    val selectedColumn = compiledSelect.selectedColumn
    private val sql = compiledSelect.sql
    private val args = compiledSelect.args
    private val observedTables = compiledSelect.observedTables

    override fun getFromCurrentPosition(cursor: Cursor): T? {
      if (cursor is FastCursor) {
        cursor.syncWith(cursor)
        return selectedColumn.getFromCursor(cursor)
      }
      return selectedColumn.getFromCursor(cursor)
    }

    override fun rawQuery(inStream: Boolean): Cursor {
      super.rawQuery(inStream)
      val db = dbConnection.readableDatabase
      val startNanos = System.nanoTime()
      val cursor = SqlUtil.query(db, sql, args)
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return FastCursor.tryCreate(cursor)
    }

    override fun map(cursor: Cursor?) = cursor

    override fun execute() = rawQuery(false)

    override fun observe() = SingleItemQueryObservable(
      createQueryObservable(
        observedTables = observedTables,
        query = this
      )
    )

    override fun toString() = "[CURSOR 1; sql=$sql]"
  }
}
