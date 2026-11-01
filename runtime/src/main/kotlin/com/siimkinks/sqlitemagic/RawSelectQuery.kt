package com.siimkinks.sqlitemagic

import android.database.SQLException
import androidx.annotation.CheckResult
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.prepareQuery

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
 * Read the results with `readAs`, or with [useRows] when the columns are known only when the query runs. Read
 * columns that can hold SQL NULL with a nullable type, such as `SqlType.STRING.nullable`.
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
   * once. Defined tables must include the temporary tables and views that the query reads, or it fails with "no
   * such table". Calling this method again replaces the previously defined tables.
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
   * Execute this query against a database and read its rows inside [block], for SQL whose columns are known only when
   * it runs. Read queries with known columns with `readAs` instead.
   *
   * The rows are valid only inside [block], on the calling thread. The query holds a database connection until
   * [block] returns, so keep [block] short. This method runs synchronously in the calling thread.
   *
   * Example:
   * ```
   * val columnNames = SELECT.RAW("PRAGMA table_info(book)").useRows { rows ->
   *   buildList {
   *     while (rows.step()) {
   *       add(rows.read(1, SqlType.STRING))
   *     }
   *   }
   * }
   * ```
   *
   * @param block Reads the rows of the query
   * @return Result of [block]
   */
  fun <R> useRows(block: (RawRows) -> R): R {
    val prepared = prepare(readWidth = 0)
    return prepared.withStatement(
      dbConnection = prepared.connection.orDefault(),
      values = prepared.bind(emptyList())
    ) { RawRows(it).use(block) }
  }

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
  ): RawSelectQuery<Row2<A, B>, O> = readColumns(
    first.columnRead,
    second.columnRead
  ) { it.read2(::Row2) }

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
  ): RawSelectQuery<Row3<A, B, C>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead
  ) { it.read3(::Row3) }

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
  ): RawSelectQuery<Row4<A, B, C, D>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead
  ) { it.read4(::Row4) }

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
  ): RawSelectQuery<Row5<A, B, C, D, E>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead,
    fifth.columnRead
  ) { it.read5(::Row5) }

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
  ): RawSelectQuery<Row6<A, B, C, D, E, F>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead,
    fifth.columnRead,
    sixth.columnRead
  ) { it.read6(::Row6) }

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
  ): RawSelectQuery<Row7<A, B, C, D, E, F, G>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead,
    fifth.columnRead,
    sixth.columnRead,
    seventh.columnRead
  ) { it.read7(::Row7) }

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
  ): RawSelectQuery<Row8<A, B, C, D, E, F, G, H>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead,
    fifth.columnRead,
    sixth.columnRead,
    seventh.columnRead,
    eighth.columnRead
  ) { it.read8(::Row8) }

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
  ): RawSelectQuery<Row9<A, B, C, D, E, F, G, H, I>, O> = readColumns(
    first.columnRead,
    second.columnRead,
    third.columnRead,
    fourth.columnRead,
    fifth.columnRead,
    sixth.columnRead,
    seventh.columnRead,
    eighth.columnRead,
    ninth.columnRead
  ) { it.read9(::Row9) }

  internal fun prepare(readWidth: Int) = prepareQuery(
    renderable = raw,
    connection = connection,
    readWidth = readWidth
  )

  private inline fun <T> readColumns(
    vararg reads: (SQLiteStatement, Int) -> Any?,
    read: (List<(SQLiteStatement, Int) -> Any?>) -> (SQLiteStatement, Int) -> T
  ): RawSelectQuery<T, O> {
    val columns = reads.asList()
    return RawSelectQuery(
      select = this,
      columns = columns,
      read = read(columns)
    )
  }
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
  internal val columns: List<(SQLiteStatement, Int) -> Any?>,
  internal val read: (SQLiteStatement, Int) -> T
) {
  private val reader = ExecutionReader { read }

  /**
   * Define tables that this query reads.
   *
   * Observed queries are re-run when these tables change. Without defined tables `observe` runs the query only
   * once. Defined tables must include the temporary tables and views that the query reads, or it fails with "no
   * such table". Calling this method again replaces the previously defined tables.
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
   * Compile and execute this query against a database and read its rows inside [block].
   *
   * The rows are read lazily, one at a time, while [block] iterates them. They can be iterated once and only inside
   * [block], on the calling thread. The query holds a database connection until [block] returns, so keep [block]
   * short. This method runs synchronously in the calling thread.
   *
   * Example:
   * ```
   * val firstTitle = (SELECT RAW "SELECT title FROM book ORDER BY title")
   *     .readAs(SqlType.STRING)
   *     .useRows { titles -> titles.firstOrNull() }
   * ```
   *
   * @param block Reads the rows of the query
   * @return Result of [block]
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun <R> useRows(block: (Sequence<T>) -> R): R = compile().useRows(block)

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

  internal fun <M> mapped(read: (SQLiteStatement, Int) -> M) = RawSelectQuery(
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
  read: (SQLiteStatement, Int) -> T
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
  fun <M> mapTo(transform: (T) -> M) = mapped { statement, index -> transform(read(statement, index)) }
}

