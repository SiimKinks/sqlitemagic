package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.connection.ConnectionOptions
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.SqlUtil
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderable
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import com.siimkinks.sqlitemagic.internal.sql.prepareQuery
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException

internal class TestGeneratedDatabase(
  private val tableCount: Int,
  private val submoduleTableCounts: Map<String, Int> = emptyMap(),
  private val schema: List<String> = emptyList(),
  private val temporarySchema: List<String> = emptyList(),
  private val cascadeTables: Map<String, Array<String>> = emptyMap()
) : GeneratedDatabase {
  override fun applySchemaPragmas(connection: SQLiteConnection) = Unit

  override fun createSchema(connection: SQLiteConnection) = schema.forEach(connection::execSQL)

  override fun createTemporarySchema(connection: SQLiteConnection) = temporarySchema.forEach(connection::execSQL)

  override fun clearData(connection: SQLiteConnection) = MutableScatterSet<String>()

  override fun collectCascadeTables(cascadeTables: MutableScatterMap<String, Array<String>>) =
    this.cascadeTables.forEach { (table, tables) -> cascadeTables[table] = tables }

  override fun migrateViews(connection: SQLiteConnection) = Unit

  override val submoduleNames
    get() = submoduleTableCounts
      .keys
      .toTypedArray()
      .takeIf(Array<String>::isNotEmpty)

  override fun getNrOfTables(moduleName: String?) = when (moduleName) {
    null, "" -> tableCount
    else -> submoduleTableCounts.getValue(moduleName)
  }

  override val dbVersion = 1

  override val dbName = "test.db"

  override val isDebug = false
}

internal fun testDbConnection(
  database: GeneratedDatabase = TestGeneratedDatabase(tableCount = 1),
  driver: SQLiteDriver = BundledSQLiteDriver(),
  fileName: String = MEMORY_DATABASE,
  adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = MutableScatterMap(initialCapacity = 0)
) = DbConnectionImpl(
  database = database,
  schema = DatabaseSchema(
    resources = { throw FileNotFoundException(it.name) },
    database = database,
    downgrader = DefaultDbDowngrader(database)
  ),
  driver = driver,
  fileName = fileName,
  options = ConnectionOptions(),
  adapterSettings = adapterSettings
)

internal class TestDatabase(
  vararg schema: String,
  adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = MutableScatterMap(initialCapacity = 0)
) : AutoCloseable {
  val connection = testDbConnection(
    database = TestGeneratedDatabase(
      tableCount = 1,
      schema = schema.asList()
    ),
    adapterSettings = adapterSettings
  )

  fun execSQL(sql: String) = connection.write { it.connection.execSQL(sql) }

  fun createViews(vararg views: GeneratedView) = connection.write {
    SqlUtil.createViews(
      connection = it.connection,
      views = views.asList(),
      temporary = false
    )
  }

  fun <T> query(sql: String, read: (SQLiteStatement) -> T) = connection.read(requiresWriter = false) {
    it.connection.query(
      sql = sql,
      read = read
    )
  }

  override fun close() = connection.pool.close()
}

internal fun <T> SQLiteConnection.query(sql: String, read: (SQLiteStatement) -> T) = prepare(sql).use { statement ->
  buildList {
    while (statement.step()) {
      add(read(statement))
    }
  }
}

internal class SingleConnectionDriver(private val connection: SQLiteConnection) : SQLiteDriver {
  override fun open(fileName: String) = connection
}

internal class QueryingDriver(
  private val answers: (String) -> Boolean,
  private val onQuery: () -> SQLiteStatement
) : SQLiteDriver {
  private val driver = BundledSQLiteDriver()

  override fun open(fileName: String): SQLiteConnection = driver.open(fileName).let { connection ->
    object : SQLiteConnection by connection {
      override fun prepare(sql: String) = when {
        answers(sql) -> QueriedStatement(onQuery)
        else -> connection.prepare(sql)
      }
    }
  }
}

private class QueriedStatement(private val onQuery: () -> SQLiteStatement) : SQLiteStatement {
  private var rows: SQLiteStatement? = null

  private val current get() = rows ?: onQuery().also { rows = it }

  override fun step() = current.step()

  override fun getBlob(index: Int) = current.getBlob(index)

  override fun getDouble(index: Int) = current.getDouble(index)

  override fun getLong(index: Int) = current.getLong(index)

  override fun getText(index: Int) = current.getText(index)

  override fun isNull(index: Int) = current.isNull(index)

  override fun getColumnCount() = current.getColumnCount()

  override fun getColumnName(index: Int) = current.getColumnName(index)

  override fun getColumnType(index: Int) = current.getColumnType(index)

  override fun bindBlob(index: Int, value: ByteArray) = Unit

  override fun bindDouble(index: Int, value: Double) = Unit

  override fun bindLong(index: Int, value: Long) = Unit

  override fun bindText(index: Int, value: String) = Unit

  override fun bindNull(index: Int) = Unit

  override fun reset() {
    rows = null
  }

  override fun clearBindings() = Unit

  override fun close() = Unit
}

internal fun emptyPrepared() = prepareQuery(
  renderable = object : SqlRenderable {
    override fun render(context: SqlRenderingContext) = Unit
  },
  connection = null
)

internal fun rowStatement() = mock<SQLiteStatement>().also {
  whenever(it.step()).thenReturn(true)
}

internal fun <T> planQuery(
  connection: DbConnectionImpl,
  plan: SelectPlan<T>,
  statement: SQLiteStatement,
  beforeRead: () -> Unit = {}
): Query<T> = object : Query<T>(connection) {
  override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): T {
    beforeRead()
    return plan.mapRows(
      statement = statement,
      isDisposed = isDisposed
    )
  }
}

internal fun listQuery(
  connection: DbConnectionImpl,
  statement: SQLiteStatement,
  mapper: (SQLiteStatement) -> String?,
  beforeRead: () -> Unit = {}
) = planQuery(
  connection = connection,
  plan = CompiledSelect(
    prepared = emptyPrepared(),
    reader = { ValueRead { rows, _ -> mapper(rows) } }
  ),
  statement = statement,
  beforeRead = beforeRead
)
