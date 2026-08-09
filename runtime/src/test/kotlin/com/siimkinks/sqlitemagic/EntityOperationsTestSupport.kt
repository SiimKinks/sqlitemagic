package com.siimkinks.sqlitemagic

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
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
import com.siimkinks.sqlitemagic.exception.OperationFailedException
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
import com.siimkinks.sqlitemagic.internal.PersistBuilder
import com.siimkinks.sqlitemagic.internal.PersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.UpdateBuilder
import com.siimkinks.sqlitemagic.internal.UpdateByColumnBuilder
import io.reactivex.schedulers.Schedulers
import java.util.ArrayDeque
import java.util.Locale

internal class RecordingDatabase: SupportSQLiteDatabase {
  val compiledStatements = mutableListOf<RecordingStatement>()
  val triggerTransactions = mutableListOf<SQLiteTransactionListener>()
  val insertResults = ArrayDeque<Long>()
  val updateResults = ArrayDeque<Int>()
  var successfulTransactions = 0
    private set
  var rolledBackTransactions = 0
    private set
  var endedTransactions = 0
    private set
  private var transactionSuccessful = false

  override fun compileStatement(sql: String) = RecordingStatement(
    database = this,
    sql = sql
  )
      .also(compiledStatements::add)

  fun nextInsertResult(): Long = when {
    insertResults.isEmpty() -> 1L
    else -> insertResults.removeFirst()
  }

  fun nextUpdateResult(): Int = when {
    updateResults.isEmpty() -> 1
    else -> updateResults.removeFirst()
  }

  override fun beginTransaction() = error("Unexpected transaction method")

  override fun beginTransactionNonExclusive() = error("Unexpected transaction method")

  override fun beginTransactionWithListener(listener: SQLiteTransactionListener) {
    triggerTransactions += listener
    transactionSuccessful = false
    listener.onBegin()
  }

  override fun beginTransactionWithListenerNonExclusive(listener: SQLiteTransactionListener) =
    beginTransactionWithListener(listener)

  override fun endTransaction() {
    endedTransactions++
    val listener = triggerTransactions.removeLast()
    when {
      transactionSuccessful -> {
        successfulTransactions++
        listener.onCommit()
      }
      else -> {
        rolledBackTransactions++
        listener.onRollback()
      }
    }
    transactionSuccessful = false
  }

  override fun setTransactionSuccessful() {
    transactionSuccessful = true
  }

  override fun inTransaction() = triggerTransactions.isNotEmpty()

  override fun isDbLockedByCurrentThread() = false

  override fun yieldIfContendedSafely() = false

  override fun yieldIfContendedSafely(sleepAmount: Long) = false

  override fun getVersion() = 1

  override fun setVersion(version: Int) = Unit

  override fun getMaximumSize() = Long.MAX_VALUE

  override fun setMaximumSize(numBytes: Long) = numBytes

  override fun getPageSize() = 4096L

  override fun setPageSize(numBytes: Long) = Unit

  override fun query(query: String): Cursor = error("Unexpected query")

  override fun query(query: String, bindArgs: Array<out Any>?): Cursor = error("Unexpected query")

  override fun query(query: SupportSQLiteQuery): Cursor = error("Unexpected query")

  override fun query(
    query: SupportSQLiteQuery,
    cancellationSignal: CancellationSignal?
  ): Cursor = error("Unexpected query")

  override fun insert(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues?
  ) = error("Unexpected insert")

  override fun delete(
    table: String,
    whereClause: String?,
    whereArgs: Array<out Any>?
  ) = error("Unexpected delete")

  override fun update(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues,
    whereClause: String?,
    whereArgs: Array<out Any>?
  ) = error("Unexpected update")

  override fun execSQL(sql: String) = error("Unexpected execSQL")

  override fun execSQL(sql: String, bindArgs: Array<out Any>) = error("Unexpected execSQL")

  override fun isReadOnly() = false

  override fun isOpen() = true

  override fun needUpgrade(newVersion: Int) = false

  override fun getPath() = "test.db"

  override fun setLocale(locale: Locale) = Unit

  override fun setMaxSqlCacheSize(size: Int) = Unit

  override fun setForeignKeyConstraintsEnabled(enable: Boolean) = Unit

  override fun enableWriteAheadLogging() = false

  override fun disableWriteAheadLogging() = Unit

  override fun isWriteAheadLoggingEnabled() = false

  override fun getAttachedDbs(): MutableList<Pair<String, String>> = mutableListOf()

  override fun isDatabaseIntegrityOk() = true

  override fun close() = Unit
}

internal class RecordingStatement(
  private val database: RecordingDatabase,
  val sql: String
) : SupportSQLiteStatement {
  val bindings = linkedMapOf<Int, Any?>()
  var clearBindingsCalls = 0
    private set

  override fun bindNull(index: Int) {
    bindings[index] = null
  }

  override fun bindLong(index: Int, value: Long) {
    bindings[index] = value
  }

  override fun bindDouble(index: Int, value: Double) {
    bindings[index] = value
  }

  override fun bindString(index: Int, value: String) {
    bindings[index] = value
  }

  override fun bindBlob(index: Int, value: ByteArray) {
    bindings[index] = value
  }

  override fun clearBindings() {
    clearBindingsCalls++
    bindings.clear()
  }

  override fun execute() = Unit

  override fun executeUpdateDelete() = database.nextUpdateResult()

  override fun executeInsert() = database.nextInsertResult()

  override fun simpleQueryForLong() = 0L

  override fun simpleQueryForString() = ""

  override fun close() = Unit
}

internal class RecordingOpenHelper(
  val database: RecordingDatabase
) : SupportSQLiteOpenHelper {
  override fun getDatabaseName() = "test.db"

  override fun setWriteAheadLoggingEnabled(enabled: Boolean) = Unit

  override fun getWritableDatabase() = database

  override fun getReadableDatabase() = database

  override fun close() = Unit
}

internal class TestGeneratedDatabase(
  private val tableCount: Int
) : GeneratedDatabase {
  override fun configureDatabase(db: SupportSQLiteDatabase) = Unit

  override fun createSchema(db: SupportSQLiteDatabase) = Unit

  override fun clearData(db: SupportSQLiteDatabase): com.siimkinks.sqlitemagic.internal.StringArraySet? =
    null

  override fun migrateViews(db: SupportSQLiteDatabase) = Unit

  override fun getSubmoduleNames(): Array<String>? = null

  override fun getNrOfTables(moduleName: String?) = tableCount

  override fun getDbVersion() = 1

  override fun getDbName() = "test.db"

  override fun <V> columnForValue(value: V): Column<V, V, V, *, NotNullable> = error("Unused")

  override fun isDebug() = false
}

internal data class RecordingConnection(
  val recordingDatabase: RecordingDatabase,
  private val tableCount: Int = 1
) {
  val connection = DbConnectionImpl(
    TestGeneratedDatabase(tableCount),
    RecordingOpenHelper(recordingDatabase),
    Schedulers.trampoline()
  )
  val triggers = mutableListOf<Set<String>>()

  init {
    connection.triggers.subscribe(triggers::add)
  }
}

internal fun newConnection() = RecordingConnection(RecordingDatabase())

internal fun newRecursiveConnection() = RecordingConnection(
  recordingDatabase = RecordingDatabase(),
  tableCount = 3
)

internal data class TransactionResult<R>(
  val result: R,
  val committed: Int,
  val rolledBack: Int
)

internal fun <R> RecordingConnection.transactionResult(result: R) = TransactionResult(
  result = result,
  committed = recordingDatabase.successfulTransactions,
  rolledBack = recordingDatabase.rolledBackTransactions
)

internal val RecordingConnection.statementSql
  get() = recordingDatabase.compiledStatements.map(RecordingStatement::sql)

internal val RecordingConnection.statementBindings
  get() = recordingDatabase.compiledStatements.map(RecordingStatement::bindings)

internal fun <M> EntityAdapter<M>.insert(entity: M): EntityInsertBuilder = InsertBuilder(
  adapter = this,
  entity = entity
)

internal fun <M> EntityAdapter<M>.bulkInsert(entities: Iterable<M>): EntityBulkInsertBuilder = BulkInsertBuilder(
  adapter = this,
  entities = entities
)

internal fun <M> EntityAdapter<M>.deleteTable(): EntityDeleteTableBuilder = DeleteTableBuilder(
  adapter = this
)

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
  entity = entity,
  byColumn = defaultIdentityColumn
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
  entities = entities,
  byColumn = defaultIdentityColumn
)

internal fun assertSingleOperationFailure(block: () -> Unit) {
  val exception = try {
    block()
    null
  } catch (exception: OperationFailedException) {
    exception
  }
  assertThat(exception).isNotNull()
}
