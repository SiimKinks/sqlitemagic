package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.Size
import com.siimkinks.sqlitemagic.CompiledSelectImpl.Companion.createQueryObservable
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_STRINGS
import java.util.concurrent.TimeUnit.NANOSECONDS

/** Builder for raw SQL SELECT statement. */
class RawSelect internal constructor(
  sql: String
) : RawSelectNode<RawSelect, CompiledRawSelect>(Builder(sql)) {
  /**
   * Define tables that are involved in this SQL SELECT statement.
   *
   * These tables are used in rx operations where subscribers will receive an immediate notification for initial data
   * as well as subsequent notifications for when the supplied `table`'s data changes.
   *
   * @param tables Tables to select from. This param must be one of annotation processor generated table objects that
   * corresponds to table in a database
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  fun from(@Size(min = 1) vararg tables: Table<*>) = From(
    select = this,
    observedTables = Array(tables.size) { index ->
      tables[index].nameInQuery
    }
  )

  /**
   * Define tables that are involved in this SQL SELECT statement.
   *
   * These tables are used in rx operations where subscribers will receive an immediate notification for initial data
   * as well as subsequent notifications for when the supplied `table`'s data changes.
   *
   * @param tables Tables to select from. This param must be one of annotation processor generated table objects that
   * corresponds to table in a database
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  fun <T : Table<*>> from(@Size(min = 1) tables: Collection<T>) = From(
    select = this,
    observedTables = tables
      .map { it.nameInQuery }
      .toTypedArray()
  )

  /** Builder for raw SQL SELECT statement. */
  class From internal constructor(
    select: RawSelect,
    observedTables: Array<String>
  ) : RawSelectNode<From, CompiledObservableRawSelect>(select.rawSelectBuilder) {
    init {
      rawSelectBuilder.observedTables = observedTables
    }

    /**
     * Create an observable which will notify subscribers with a [Query] for execution.
     *
     * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
     * the supplied `table`'s data changes through the SqliteMagic provided model operations. Unsubscribe when you no
     * longer want updates to a query.
     *
     * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
     * [io.reactivex.Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
     * the immediate notification sent on subscribe also uses this scheduler. As such, calling
     * [io.reactivex.Observable.subscribeOn] on the returned observable has no effect.
     *
     * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
     * `skip(1)` on the returned observable.
     *
     * One might want to explore the returned type methods for convenience query related operators.
     *
     * **Warning:** this method does not perform the query! Only by subscribing to the returned
     * [io.reactivex.Observable] will the operation occur.
     */
    @CheckResult
    fun observe(): SingleItemQueryObservable<Cursor> =
      CompiledRawSelectImpl(builder = rawSelectBuilder)
        .observe()
  }

  internal class Builder internal constructor(
    @JvmField
    val sql: String
  ) {
    @JvmField
    var args: Array<out String>? = null

    @JvmField
    var observedTables: Array<String> = EMPTY_STRINGS

    @JvmField
    var dbConnection: DbConnectionImpl = SqliteMagic.getDefaultDbConnection()
  }

  internal class CompiledRawSelectImpl internal constructor(
    builder: Builder
  ) : Query.DatabaseQuery<Cursor, Cursor>(
    dbConnection = builder.dbConnection,
    mapper = null
  ), CompiledObservableRawSelect {
    @JvmField
    val sql: String = builder.sql

    @JvmField
    val args: Array<out String>? = builder.args

    @JvmField
    val observedTables: Array<String> = builder.observedTables

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

    override fun execute(): Cursor = rawQuery(inStream = false)

    override fun observe(): SingleItemQueryObservable<Cursor> = SingleItemQueryObservable(
      createQueryObservable(
        observedTables = observedTables,
        query = this
      )
    )

    override fun toString() = "[RAW; sql=$sql]"
  }
}
