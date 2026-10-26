package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.prepareQuery
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single

/**
 * Whether a raw SQL query defines the tables it reads, which decides what its `observe` returns.
 */
sealed interface RawTables

/**
 * Raw SQL query that defines the tables it reads with `observes`.
 */
sealed interface DeclaredTables : RawTables

/**
 * Raw SQL query that does not define the tables it reads.
 */
sealed interface UndeclaredTables : RawTables

/**
 * Builder for raw SQL SELECT statement.
 *
 * Read the results with `readAs` or get a cursor with [toCursor]. Read columns that can hold SQL NULL with a nullable
 * type, such as `SqlType.STRING.nullable`.
 *
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class RawSelect<O : RawTables> internal constructor(
  private val raw: ExpressionNode.Raw,
  private val connection: DbConnectionImpl? = null
) {
  /**
   * Define tables that this query reads.
   *
   * Observed queries are re-run when these tables change. Without defined tables `observe` runs the query only
   * once. Calling this method again replaces the previously defined tables.
   *
   * @param table Table that this query reads
   * @param tables Additional tables that this query reads
   * @return Raw SQL SELECT statement builder
   */
  @CheckResult
  fun observes(table: Table<*>, vararg tables: Table<*>) = RawSelect<DeclaredTables>(
    raw = raw.copy(
      dependencies = QueryDependencies.declared(
        table = table,
        tables = tables
      )
    ),
    connection = connection
  )

  /**
   * Configure this query to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return Raw SQL SELECT statement builder
   */
  @CheckResult
  fun usingConnection(connection: DbConnection) = RawSelect<O>(
    raw = raw,
    connection = connection as DbConnectionImpl
  )

  /**
   * Read the single column of each row.
   *
   * Example:
   * ```
   * (SELECT RAW "SELECT count(*) FROM author").readAs(SqlType.LONG)
   * ```
   *
   * @param descriptor Type of the column, for example `SqlType.LONG`, or a column whose values are read the same way
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <T> readAs(descriptor: ReadContract<*, *, T>) = RawColumnQuery(
    select = this,
    read = descriptor.columnRead
  )

  /**
   * Read two columns of each row by position.
   *
   * Rows are returned as [Row2].
   *
   * Example:
   * ```
   * SELECT
   *     .RAW("SELECT id, name FROM author")
   *     .readAs(SqlType.LONG, SqlType.STRING.nullable)
   * ```
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>
  ) = readColumns(
    columns = listOf(first.columnRead, second.columnRead),
    read = read2(
      readA = first.columnRead,
      readB = second.columnRead,
      create = ::Row2
    )
  )

  /**
   * Read three columns of each row by position.
   *
   * Rows are returned as [Row3].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>
  ) = readColumns(
    columns = listOf(first.columnRead, second.columnRead, third.columnRead),
    read = read3(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      create = ::Row3
    )
  )

  /**
   * Read four columns of each row by position.
   *
   * Rows are returned as [Row4].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>
  ) = readColumns(
    columns = listOf(first.columnRead, second.columnRead, third.columnRead, fourth.columnRead),
    read = read4(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      create = ::Row4
    )
  )

  /**
   * Read five columns of each row by position.
   *
   * Rows are returned as [Row5].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @param fifth Type of the fifth column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D, E> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>,
    fifth: ReadContract<*, *, E>
  ) = readColumns(
    columns = listOf(
      first.columnRead,
      second.columnRead,
      third.columnRead,
      fourth.columnRead,
      fifth.columnRead
    ),
    read = read5(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      readE = fifth.columnRead,
      create = ::Row5
    )
  )

  /**
   * Read six columns of each row by position.
   *
   * Rows are returned as [Row6].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @param fifth Type of the fifth column
   * @param sixth Type of the sixth column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D, E, F> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>,
    fifth: ReadContract<*, *, E>,
    sixth: ReadContract<*, *, F>
  ) = readColumns(
    columns = listOf(
      first.columnRead,
      second.columnRead,
      third.columnRead,
      fourth.columnRead,
      fifth.columnRead,
      sixth.columnRead
    ),
    read = read6(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      readE = fifth.columnRead,
      readF = sixth.columnRead,
      create = ::Row6
    )
  )

  /**
   * Read seven columns of each row by position.
   *
   * Rows are returned as [Row7].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @param fifth Type of the fifth column
   * @param sixth Type of the sixth column
   * @param seventh Type of the seventh column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D, E, F, G> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>,
    fifth: ReadContract<*, *, E>,
    sixth: ReadContract<*, *, F>,
    seventh: ReadContract<*, *, G>
  ) = readColumns(
    columns = listOf(
      first.columnRead,
      second.columnRead,
      third.columnRead,
      fourth.columnRead,
      fifth.columnRead,
      sixth.columnRead,
      seventh.columnRead
    ),
    read = read7(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      readE = fifth.columnRead,
      readF = sixth.columnRead,
      readG = seventh.columnRead,
      create = ::Row7
    )
  )

  /**
   * Read eight columns of each row by position.
   *
   * Rows are returned as [Row8].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @param fifth Type of the fifth column
   * @param sixth Type of the sixth column
   * @param seventh Type of the seventh column
   * @param eighth Type of the eighth column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>,
    fifth: ReadContract<*, *, E>,
    sixth: ReadContract<*, *, F>,
    seventh: ReadContract<*, *, G>,
    eighth: ReadContract<*, *, H>
  ) = readColumns(
    columns = listOf(
      first.columnRead,
      second.columnRead,
      third.columnRead,
      fourth.columnRead,
      fifth.columnRead,
      sixth.columnRead,
      seventh.columnRead,
      eighth.columnRead
    ),
    read = read8(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      readE = fifth.columnRead,
      readF = sixth.columnRead,
      readG = seventh.columnRead,
      readH = eighth.columnRead,
      create = ::Row8
    )
  )

  /**
   * Read nine columns of each row by position.
   *
   * Rows are returned as [Row9].
   *
   * @param first Type of the first column
   * @param second Type of the second column
   * @param third Type of the third column
   * @param fourth Type of the fourth column
   * @param fifth Type of the fifth column
   * @param sixth Type of the sixth column
   * @param seventh Type of the seventh column
   * @param eighth Type of the eighth column
   * @param ninth Type of the ninth column
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H, I> readAs(
    first: ReadContract<*, *, A>,
    second: ReadContract<*, *, B>,
    third: ReadContract<*, *, C>,
    fourth: ReadContract<*, *, D>,
    fifth: ReadContract<*, *, E>,
    sixth: ReadContract<*, *, F>,
    seventh: ReadContract<*, *, G>,
    eighth: ReadContract<*, *, H>,
    ninth: ReadContract<*, *, I>
  ) = readColumns(
    columns = listOf(
      first.columnRead,
      second.columnRead,
      third.columnRead,
      fourth.columnRead,
      fifth.columnRead,
      sixth.columnRead,
      seventh.columnRead,
      eighth.columnRead,
      ninth.columnRead
    ),
    read = read9(
      readA = first.columnRead,
      readB = second.columnRead,
      readC = third.columnRead,
      readD = fourth.columnRead,
      readE = fifth.columnRead,
      readF = sixth.columnRead,
      readG = seventh.columnRead,
      readH = eighth.columnRead,
      readI = ninth.columnRead,
      create = ::Row9
    )
  )

  /**
   * Compile raw SQL query and instruct it to return raw [Cursor] when query is executed.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled raw SQL query
   */
  @CheckResult
  fun toCursor() = CompiledRawCursor<O>(
    CompiledCursor(
      prepared = prepare(readWidth = 0),
      reader = UNREAD_ROWS
    )
  )

  internal fun prepare(readWidth: Int) = prepareQuery(
    renderable = raw,
    connection = connection,
    readWidth = readWidth
  )

  private fun <T> readColumns(
    columns: List<(Cursor, Int) -> Any?>,
    read: (Cursor, Int) -> T
  ) = RawSelectQuery(
    select = this,
    columns = columns,
    read = read
  )
}

/**
 * Raw SQL SELECT statement.
 *
 * Columns of each row are read by position, with the types given to `readAs`.
 *
 * @param T Row type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
open class RawSelectQuery<T, O : RawTables> internal constructor(
  internal val select: RawSelect<O>,
  internal val columns: List<(Cursor, Int) -> Any?>,
  internal val read: (Cursor, Int) -> T
) {
  private val reader = ExecutionReader { read }

  /**
   * Define tables that this query reads.
   *
   * Observed queries are re-run when these tables change. Without defined tables `observe` runs the query only
   * once. Calling this method again replaces the previously defined tables.
   *
   * @param table Table that this query reads
   * @param tables Additional tables that this query reads
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  open fun observes(table: Table<*>, vararg tables: Table<*>): RawSelectQuery<T, DeclaredTables> = RawSelectQuery(
    select = select.observes(table, *tables),
    columns = columns,
    read = read
  )

  /**
   * Configure this query to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  open fun usingConnection(connection: DbConnection): RawSelectQuery<T, O> = RawSelectQuery(
    select = select.usingConnection(connection),
    columns = columns,
    read = read
  )

  /**
   * Compile raw SQL query.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled raw SQL query
   */
  @CheckResult
  fun compile() = CompiledRawSelect<T, O>(
    CompiledSelect(
      prepared = prepare(),
      reader = reader
    )
  )

  /**
   * Compile and execute this query against a database.
   *
   * Returned value will never be `null`. If query returns no rows then resulting list will be empty.
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun execute() = compile().execute()

  /**
   * Compile raw SQL query and instruct it to take only the first row from the result set.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * The SQL is not modified, so add a LIMIT clause to the SQL when needed.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled raw SQL query
   */
  @CheckResult
  fun takeFirst() = CompiledRawFirst<T, O>(
    CompiledFirst(
      prepared = prepare(),
      reader = reader
    )
  )

  /**
   * Compile raw SQL query and instruct it to return raw [Cursor] when query is executed.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled raw SQL query
   */
  @CheckResult
  fun toCursor() = CompiledRawReadCursor<T, O>(
    CompiledCursor(
      prepared = prepare(),
      reader = reader
    )
  )

  internal fun <M> mapped(read: (Cursor, Int) -> M) = RawSelectQuery(
    select = select,
    columns = columns,
    read = read
  )

  private fun prepare() = select.prepare(readWidth = columns.size)
}

/**
 * Raw SQL SELECT statement that reads a single column.
 *
 * Map the column values with [mapTo].
 *
 * @param T Column type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class RawColumnQuery<T, O : RawTables> internal constructor(
  select: RawSelect<O>,
  read: (Cursor, Int) -> T
) : RawSelectQuery<T, O>(
  select = select,
  columns = listOf(read),
  read = read
) {
  @CheckResult
  override fun observes(table: Table<*>, vararg tables: Table<*>) = RawColumnQuery(
    select = select.observes(table, *tables),
    read = read
  )

  @CheckResult
  override fun usingConnection(connection: DbConnection) = RawColumnQuery(
    select = select.usingConnection(connection),
    read = read
  )

  /**
   * Map each column value with [transform].
   *
   * Example:
   * ```
   * SELECT
   *     .RAW("SELECT id FROM author")
   *     .readAs(SqlType.LONG)
   *     .mapTo(::AuthorId)
   * ```
   *
   * @param transform Function that creates a result from the column value
   * @return Raw SQL SELECT statement
   */
  @CheckResult
  fun <M> mapTo(transform: (T) -> M) = mapped { cursor, index -> transform(read(cursor, index)) }
}

private val UNREAD_ROWS = ExecutionReader<Nothing> { error("A raw query without readAs reads no rows") }

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
 * the immediate notification sent on subscribe also uses this scheduler. As such, calling
 * [Observable.subscribeOn] on the returned observable has no effect.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawQuery")
fun <T> RawSelectQuery<T, DeclaredTables>.observe() = compile().observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits its result to
 * downstream only once.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to
 * [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn], so calling `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawQuery")
fun <T> RawSelectQuery<T, UndeclaredTables>.observe() = compile().observe()
