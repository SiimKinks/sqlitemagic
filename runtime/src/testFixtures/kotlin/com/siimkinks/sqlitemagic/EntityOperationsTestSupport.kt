package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.BulkDeleteBuilder
import com.siimkinks.sqlitemagic.internal.BulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.internal.BulkInsertBuilder
import com.siimkinks.sqlitemagic.internal.BulkPersistBuilder
import com.siimkinks.sqlitemagic.internal.BulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.BulkUpdateBuilder
import com.siimkinks.sqlitemagic.internal.BulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeleteBuilder
import com.siimkinks.sqlitemagic.internal.DeleteByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeleteTableBuilder
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.InsertBuilder
import com.siimkinks.sqlitemagic.internal.MutationResults
import com.siimkinks.sqlitemagic.internal.PersistBuilder
import com.siimkinks.sqlitemagic.internal.PersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.UpdateBuilder
import com.siimkinks.sqlitemagic.internal.UpdateByColumnBuilder

internal class RecordingSQLiteConnection : SQLiteConnection, MutationResults {
  val compiledStatements: List<RecordingStatement>
    field = mutableListOf()
  val executions: List<RecordedExecution>
    field = mutableListOf()
  val insertResults = ArrayDeque<Long>()
  val updateResults = ArrayDeque<Int>()
  var successfulTransactions = 0
    private set
  var rolledBackTransactions = 0
    private set
  var rolledBackSavepoints = 0
    private set
  private var transaction = false

  override fun prepare(sql: String): SQLiteStatement = when {
    TRANSACTION_CONTROL.any(sql::startsWith) -> TransactionControlStatement(sql)
    sql.startsWith("PRAGMA") -> ConnectionSetupStatement(sql)
    else -> RecordingStatement(
      sql = sql,
      onStep = executions::add
    ).also(compiledStatements::add)
  }

  override fun inTransaction() = transaction

  override fun executeInsert(statement: SQLiteStatement): Long {
    statement.step()
    return insertResults.removeFirstOrNull() ?: 1L
  }

  override fun executeUpdateDelete(statement: SQLiteStatement): Int {
    statement.step()
    return updateResults.removeFirstOrNull() ?: 1
  }

  override fun close() = Unit

  private inner class TransactionControlStatement(private val sql: String) : NoRowStatement() {
    override fun step(): Boolean {
      when {
        sql.startsWith("BEGIN") -> transaction = true
        sql.startsWith("SAVEPOINT") || sql.startsWith("RELEASE") -> Unit
        sql.startsWith("ROLLBACK TO") -> rolledBackSavepoints++
        sql == "COMMIT" -> {
          transaction = false
          successfulTransactions++
        }
        else -> {
          transaction = false
          rolledBackTransactions++
        }
      }
      return false
    }
  }

  private class ConnectionSetupStatement(private val sql: String) : NoRowStatement() {
    override fun step() = sql == USER_VERSION

    override fun getLong(index: Int) = 1L
  }
}

internal data class RecordedExecution(
  val sql: String,
  val bindings: Map<Int, Any?>
)

internal class RecordingStatement(
  val sql: String,
  private val onStep: (RecordedExecution) -> Unit = {}
) : NoRowStatement() {
  private val boundValues = linkedMapOf<Int, Any?>()
  var bindings = emptyMap<Int, Any?>()
    private set
  var closeCalls = 0
    private set

  override fun bindNull(index: Int) {
    boundValues[index] = null
  }

  override fun bindLong(index: Int, value: Long) {
    boundValues[index] = value
  }

  override fun bindDouble(index: Int, value: Double) {
    boundValues[index] = value
  }

  override fun bindText(index: Int, value: String) {
    boundValues[index] = value
  }

  override fun bindBlob(index: Int, value: ByteArray) {
    boundValues[index] = value
  }

  override fun clearBindings() = boundValues.clear()

  override fun step(): Boolean {
    bindings = boundValues.toMap()
    onStep(
      RecordedExecution(
        sql = sql,
        bindings = bindings
      )
    )
    return false
  }

  override fun close() {
    closeCalls++
  }
}

internal abstract class NoRowStatement : SQLiteStatement {
  override fun bindBlob(index: Int, value: ByteArray) = Unit

  override fun bindDouble(index: Int, value: Double) = Unit

  override fun bindLong(index: Int, value: Long) = Unit

  override fun bindText(index: Int, value: String) = Unit

  override fun bindNull(index: Int) = Unit

  override fun getBlob(index: Int) = noRow()

  override fun getDouble(index: Int) = noRow()

  override fun getLong(index: Int): Long = noRow()

  override fun getText(index: Int): String = noRow()

  override fun isNull(index: Int) = noRow()

  override fun getColumnCount() = 0

  override fun getColumnName(index: Int) = noRow()

  override fun getColumnType(index: Int) = noRow()

  override fun reset() = Unit

  override fun clearBindings() = Unit

  override fun close() = Unit

  private fun noRow(): Nothing = error("Recorded statements return no rows")
}

internal class RecordingDbConnection(
  val sqliteConnection: RecordingSQLiteConnection,
  tableCount: Int = 1,
  cascadeTables: Map<String, Array<String>> = emptyMap()
) {
  val dbConnection = testDbConnection(
    database = TestGeneratedDatabase(
      tableCount = tableCount,
      cascadeTables = cascadeTables
    ),
    driver = SingleConnectionDriver(sqliteConnection)
  )
  private val recordedTriggers = dbConnection.recordTriggers()
  val triggers get() = recordedTriggers.triggers
  val statementSql
    get() = sqliteConnection.compiledStatements.map(RecordingStatement::sql)
  val statementBindings
    get() = sqliteConnection.compiledStatements.map(RecordingStatement::bindings)

  fun <R> transactionResult(result: R) = TransactionResult(
    result = result,
    committed = sqliteConnection.successfulTransactions,
    rolledBack = sqliteConnection.rolledBackTransactions,
    rolledBackSavepoints = sqliteConnection.rolledBackSavepoints
  )
}

internal fun newConnection() = RecordingDbConnection(RecordingSQLiteConnection())

internal fun newRecursiveConnection() = RecordingDbConnection(
  sqliteConnection = RecordingSQLiteConnection(),
  tableCount = 3
)

private val TRANSACTION_CONTROL = listOf("BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE")
private const val USER_VERSION = "PRAGMA user_version"

internal data class TransactionResult<R>(
  val result: R,
  val committed: Int,
  val rolledBack: Int,
  val rolledBackSavepoints: Int = 0
)

internal fun <T> oneShotIterable(values: Iterable<T>) = object : Iterable<T> {
  private var iteratorRequested = false

  override fun iterator(): Iterator<T> {
    check(!iteratorRequested) { "Iterator requested more than once" }
    iteratorRequested = true
    return values.iterator()
  }
}

internal fun <M> EntityAdapter<M>.insert(entity: M): EntityInsertBuilder = InsertBuilder(
  adapter = this,
  entity = entity
)

internal fun <M> EntityAdapter<M>.bulkInsert(entities: Iterable<M>): EntityBulkInsertBuilder = BulkInsertBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityAdapter<M>.deleteTable(): EntityDeleteTableBuilder = DeleteTableBuilder(adapter = this)

internal fun <M> EntityIdentityAdapter<M>.updateByColumn(entity: M): EntityUpdateByColumnBuilder<M> =
  UpdateByColumnBuilder(
    adapter = this,
    entity = entity
  )

internal fun <M> EntityIdentityAdapter<M>.persistByColumn(entity: M): EntityPersistByColumnBuilder<M> =
  PersistByColumnBuilder(
    adapter = this,
    entity = entity
  )

internal fun <M> EntityIdentityAdapter<M>.deleteByColumn(entity: M): EntityDeleteByColumnBuilder<M> =
  DeleteByColumnBuilder(
    adapter = this,
    entity = entity
  )

internal fun <M> EntityIdentityAdapter<M>.bulkUpdateByColumn(
  entities: Iterable<M>
): EntityBulkUpdateByColumnBuilder<M> = BulkUpdateByColumnBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityIdentityAdapter<M>.bulkPersistByColumn(
  entities: Iterable<M>
): EntityBulkPersistByColumnBuilder<M> = BulkPersistByColumnBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityIdentityAdapter<M>.bulkDeleteByColumn(
  entities: Collection<M>
): EntityBulkDeleteByColumnBuilder<M> = BulkDeleteByColumnBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityDefaultIdentityAdapter<M>.update(entity: M): EntityUpdateBuilder = UpdateBuilder(
  adapter = this,
  entity = entity
)

internal fun <M> EntityDefaultIdentityAdapter<M>.persist(entity: M): EntityPersistBuilder = PersistBuilder(
  adapter = this,
  entity = entity
)

internal fun <M> EntityDefaultIdentityAdapter<M>.delete(entity: M): EntityDeleteBuilder = DeleteBuilder(
  adapter = this,
  entity = entity
)

internal fun <M> EntityDefaultIdentityAdapter<M>.bulkUpdate(
  entities: Iterable<M>
): EntityBulkUpdateBuilder = BulkUpdateBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityDefaultIdentityAdapter<M>.bulkPersist(
  entities: Iterable<M>
): EntityBulkPersistBuilder = BulkPersistBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityDefaultIdentityAdapter<M>.bulkDelete(
  entities: Collection<M>
): EntityBulkDeleteBuilder = BulkDeleteBuilder(
  adapter = this,
  entities = entities
)
