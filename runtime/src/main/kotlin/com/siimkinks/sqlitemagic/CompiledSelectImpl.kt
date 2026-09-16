package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.Query.DatabaseQuery
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import io.reactivex.Observable
import io.reactivex.functions.Predicate
import java.util.concurrent.TimeUnit.NANOSECONDS

internal class CompiledSelectImpl<T, S>(
  @JvmField
  val sql: String,
  @JvmField
  val args: Array<String>?,
  @JvmField
  val table: Table<T>,
  dbConnection: DbConnectionImpl,
  @JvmField
  val observedTables: Array<String>,
  @JvmField
  val columns: SimpleArrayMap<String, Int>?,
  @JvmField
  val tableGraphNodeNames: SimpleArrayMap<String, String>?,
  @JvmField
  val queryDeep: Boolean
) : DatabaseQuery<List<T>, T>(
  dbConnection,
  table.mapper(columns, tableGraphNodeNames, queryDeep)
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
      val mapper = checkNotNull(mapper)
      return buildList(rowCount) {
        while (cursor.moveToNext()) {
          add(mapper.apply(cursor))
        }
      }
    }
  }

  override fun toString() = "[deepQuery=$queryDeep;sql=$sql]"

  override fun execute() = map(rawQuery(false))

  override fun observe() = ListQueryObservable(
    createQueryObservable(
      observedTables = observedTables,
      query = this
    )
  )

  override fun takeFirst() = CompiledFirstSelectImpl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  override fun count(): CompiledCountSelect<S> = CompiledCountSelectImpl(
    parentSql = sql,
    args = args,
    dbConnection = dbConnection,
    observedTables = observedTables
  )

  override fun toCursor() = CompiledCursorSelectImpl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  internal class CompiledCountSelectImpl<S>(
    parentSql: String,
    args: Array<String>?,
    dbConnection: DbConnectionImpl,
    observedTables: Array<String>
  ) : DatabaseQuery<Long, Long>(dbConnection, null), CompiledCountSelect<S> {
    private val countStm: SupportSQLiteStatement
    private val sql: String
    private val observedTables: Array<String>
    private val args: Array<String>?

    init {
      val sql = addCountFunction(parentSql)
      val countStm = dbConnection.compileStatement(sql)
      SqlUtil.bindAllArgsAsStrings(countStm, args)
      this.countStm = countStm
      this.sql = sql
      this.observedTables = observedTables
      this.args = args
    }

    override fun map(cursor: Cursor?) = execute()

    override fun execute(): Long {
      val count: Long
      val startNanos: Long
      synchronized(countStm) {
        startNanos = System.nanoTime()
        count = countStm.simpleQueryForLong()
      }
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return count
    }

    override fun observe() = CountQueryObservable(
      createQueryObservable(
        observedTables = observedTables,
        query = this
      )
    )

    override fun toString() = "[COUNT; sql=$sql]"

    private fun addCountFunction(sql: String): String {
      val selectEndIndex = sql.indexOf("SELECT") + 6
      val fromStartIndex = sql.indexOf("FROM", startIndex = selectEndIndex)
      return sql.replaceRange(
        startIndex = selectEndIndex,
        endIndex = fromStartIndex,
        replacement = " count(*) "
      )
    }
  }

  internal class CompiledCursorSelectImpl<T, S>(
    compiledSelect: CompiledSelectImpl<T, S>,
    dbConnection: DbConnectionImpl
  ) : DatabaseQuery<Cursor, T>(dbConnection, compiledSelect.mapper), CompiledCursorSelect<T, S> {
    private val sql = compiledSelect.sql
    private val args = compiledSelect.args
    private val observedTables = compiledSelect.observedTables
    private val queryDeep = compiledSelect.queryDeep

    override fun getFromCurrentPosition(cursor: Cursor): T? {
      val mapper = checkNotNull(mapper)
      if (cursor is FastCursor) {
        cursor.syncWith(cursor)
        return mapper.apply(cursor)
      }
      return mapper.apply(cursor)
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

    override fun toString() = "[CURSOR; deepQuery=$queryDeep;sql=$sql]"
  }

  internal class CompiledFirstSelectImpl<T, S>(
    compiledSelect: CompiledSelectImpl<T, S>,
    dbConnection: DbConnectionImpl
  ) : DatabaseQuery<T, T>(dbConnection, compiledSelect.mapper), CompiledFirstSelect<T, S> {
    @JvmField
    val sql = addTakeFirstLimitClauseIfNeeded(compiledSelect.sql)

    @JvmField
    val args = compiledSelect.args

    @JvmField
    val table = compiledSelect.table

    @JvmField
    val observedTables = compiledSelect.observedTables

    @JvmField
    val columns = compiledSelect.columns

    @JvmField
    val tableGraphNodeNames = compiledSelect.tableGraphNodeNames

    @JvmField
    val queryDeep = compiledSelect.queryDeep

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

    override fun map(cursor: Cursor?): T? {
      checkNotNull(cursor).use { cursor ->
        if (cursor.moveToNext()) {
          return checkNotNull(mapper).apply(cursor)
        }
        return null
      }
    }

    override fun execute() = map(rawQuery(false))

    override fun observe() = SingleItemQueryObservable(
      createQueryObservable(
        observedTables = observedTables,
        query = this
      )
    )

    override fun toString() = "[TAKE FIRST; deepQuery=$queryDeep;sql=$sql]"

    companion object {
      @JvmStatic
      fun addTakeFirstLimitClauseIfNeeded(sql: String): String {
        val limitIndex = sql.lastIndexOf("LIMIT")
        return when {
          limitIndex == -1 -> "${sql}LIMIT 1 "
          sql.startsWith(
            prefix = " 1 ",
            startIndex = limitIndex + "LIMIT".length
          ) -> sql
          else -> throw SQLException(
            "Tried to query first row but limit clause is already defined with limitClause != 1"
          )
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @CheckResult
    fun <T> createQueryObservable(
      observedTables: Array<String>,
      query: DatabaseQuery<T, *>
    ): Observable<Query<T>> {
      val tableFilter = when {
        observedTables.size > 1 -> Predicate<Set<String>> { triggers ->
          observedTables.any(triggers::contains)
        }
        else -> {
          val table = observedTables[0]
          Predicate<Set<String>> { table in it }
        }
      }
      val dbConnectionImpl = query.dbConnection
      return dbConnectionImpl.triggers
        .filter(tableFilter) // Only trigger on tables we care about.
        .map(query)
        .startWith(query)
        .observeOn(dbConnectionImpl.queryScheduler)
        .doOnSubscribe(dbConnectionImpl.ensureNotInTransaction)
    }
  }
}
