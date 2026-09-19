package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import androidx.annotation.CheckResult
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
  dbConnection: DbConnectionImpl?,
  @JvmField
  val observedTables: Array<String>,
  @JvmField
  val columns: SimpleArrayMap<String, Int>?,
  @JvmField
  val tableGraphNodeNames: SimpleArrayMap<String, String>?,
  @JvmField
  val queryDeep: Boolean
) : DatabaseQuery<List<T>, T>(
  dbConnection = dbConnection,
  mapper = table.mapper(columns, tableGraphNodeNames, queryDeep)
), CompiledSelect<T, S> {
  override fun rawQuery(
    inStream: Boolean,
    dbConnection: DbConnectionImpl
  ): Cursor {
    super.rawQuery(
      inStream = inStream,
      dbConnection = dbConnection
    )
    val db = dbConnection.readableDatabase
    val startNanos = System.nanoTime()
    val cursor = SqlUtil.query(db, sql, args)
    if (SqliteMagic.LOGGING_ENABLED) {
      val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
    }
    return FastCursor.tryCreate(cursor)
  }

  override fun map(
    cursor: Cursor?,
    dbConnection: DbConnectionImpl
  ): List<T> {
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

  override fun execute() = resolveConnection()
    .let { dbConnection ->
      map(
        cursor = rawQuery(
          inStream = false,
          dbConnection = dbConnection
        ),
        dbConnection = dbConnection
      )
    }

  override fun observe() = ListQueryObservable(
    createQueryObservable(
      observedTables = observedTables,
      query = this
    )
  )

  override fun takeFirst(): CompiledFirstSelect<T, S> = CompiledFirstSelectImpl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  override fun count(): CompiledCountSelect<S> = CompiledCountSelectImpl(
    parentSql = sql,
    args = args,
    dbConnection = dbConnection,
    observedTables = observedTables
  )

  override fun toCursor(): CompiledCursorSelect<T, S> = CompiledCursorSelectImpl(
    compiledSelect = this,
    dbConnection = dbConnection
  )

  internal class CompiledCountSelectImpl<S>(
    parentSql: String,
    private val args: Array<String>?,
    dbConnection: DbConnectionImpl?,
    private val observedTables: Array<String>
  ) : DatabaseQuery<Long, Long>(
    dbConnection = dbConnection,
    mapper = null
  ), CompiledCountSelect<S> {
    private val sql = addCountFunction(parentSql)
    private val countStatement = ConnectionStatement(
      sql = sql,
      args = args,
      initialConnection = dbConnection
    )

    override fun map(
      cursor: Cursor?,
      dbConnection: DbConnectionImpl
    ) = execute(dbConnection)

    override fun execute(): Long = execute(resolveConnection())

    private fun execute(dbConnection: DbConnectionImpl): Long = countStatement.execute(
      dbConnection = dbConnection,
      operation = { statement ->
        val startNanos = System.nanoTime()
        val count = statement.simpleQueryForLong()
        if (SqliteMagic.LOGGING_ENABLED) {
          val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
          LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
        }
        count
      }
    )

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
    dbConnection: DbConnectionImpl?
  ) : DatabaseQuery<Cursor, T>(
    dbConnection = dbConnection,
    mapper = compiledSelect.mapper
  ), CompiledCursorSelect<T, S> {
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

    override fun rawQuery(
      inStream: Boolean,
      dbConnection: DbConnectionImpl
    ): Cursor {
      super.rawQuery(
        inStream = inStream,
        dbConnection = dbConnection
      )
      val db = dbConnection.readableDatabase
      val startNanos = System.nanoTime()
      val cursor = SqlUtil.query(db, sql, args)
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return FastCursor.tryCreate(cursor)
    }

    override fun map(
      cursor: Cursor?,
      dbConnection: DbConnectionImpl
    ) = cursor

    override fun execute() = checkNotNull(rawQuery(false))

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
    dbConnection: DbConnectionImpl?
  ) : DatabaseQuery<T, T>(
    dbConnection = dbConnection,
    mapper = compiledSelect.mapper
  ), CompiledFirstSelect<T, S> {
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

    override fun rawQuery(
      inStream: Boolean,
      dbConnection: DbConnectionImpl
    ): Cursor {
      super.rawQuery(
        inStream = inStream,
        dbConnection = dbConnection
      )
      val db = dbConnection.readableDatabase
      val startNanos = System.nanoTime()
      val cursor = SqlUtil.query(db, sql, args)
      if (SqliteMagic.LOGGING_ENABLED) {
        val queryTimeInMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args)
      }
      return FastCursor.tryCreate(cursor)
    }

    override fun map(
      cursor: Cursor?,
      dbConnection: DbConnectionImpl
    ): T? {
      checkNotNull(cursor).use { cursor ->
        if (cursor.moveToNext()) {
          return checkNotNull(mapper).apply(cursor)
        }
        return null
      }
    }

    override fun execute() = resolveConnection()
      .let { dbConnection ->
        map(
          cursor = rawQuery(
            inStream = false,
            dbConnection = dbConnection
          ),
          dbConnection = dbConnection
        )
      }

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
      val dbConnection = query.resolveConnection()
      return dbConnection.triggers
        .filter(tableFilter) // Only trigger on tables we care about.
        .map(query)
        .startWith(query)
        .observeOn(dbConnection.queryScheduler)
        .doOnSubscribe(dbConnection.ensureNotInTransaction)
    }
  }
}
