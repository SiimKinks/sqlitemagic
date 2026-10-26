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
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.PersistBuilder
import com.siimkinks.sqlitemagic.internal.PersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.UpdateBuilder
import com.siimkinks.sqlitemagic.internal.UpdateByColumnBuilder
import io.reactivex.schedulers.Schedulers
import java.util.Locale

internal class RecordingDatabase : SupportSQLiteDatabase {
  val compiledStatements: List<RecordingStatement>
    field = mutableListOf()
  private val triggerTransactions = mutableListOf<SQLiteTransactionListener>()
  val insertResults = ArrayDeque<Long>()
  val updateResults = ArrayDeque<Int>()
  var successfulTransactions = 0
    private set
  var rolledBackTransactions = 0
    private set
  private var transactionSuccessful = false

  override fun compileStatement(sql: String) = RecordingStatement(
    database = this,
    sql = sql
  ).also(compiledStatements::add)

  override fun beginTransaction() = error("Unexpected transaction method")

  override fun beginTransactionNonExclusive() = error("Unexpected transaction method")

  override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {
    triggerTransactions += transactionListener
    transactionSuccessful = false
    transactionListener.onBegin()
  }

  override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) =
    beginTransactionWithListener(transactionListener)

  override fun endTransaction() {
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

  override val isDbLockedByCurrentThread = false

  override fun yieldIfContendedSafely() = false

  override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long) = false

  override var version = 1

  override val maximumSize = Long.MAX_VALUE

  override fun setMaximumSize(numBytes: Long) = numBytes

  override var pageSize = 4096L

  override fun query(query: String): Cursor = error("Unexpected query")

  override fun query(
    query: String,
    bindArgs: Array<out Any?>
  ): Cursor = error("Unexpected query")

  override fun query(query: SupportSQLiteQuery): Cursor = error("Unexpected query")

  override fun query(
    query: SupportSQLiteQuery,
    cancellationSignal: CancellationSignal?
  ): Cursor = error("Unexpected query")

  override fun insert(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues
  ) = error("Unexpected insert")

  override fun delete(
    table: String,
    whereClause: String?,
    whereArgs: Array<out Any?>?
  ) = error("Unexpected delete")

  override fun update(
    table: String,
    conflictAlgorithm: Int,
    values: ContentValues,
    whereClause: String?,
    whereArgs: Array<out Any?>?
  ) = error("Unexpected update")

  override fun execSQL(sql: String) = error("Unexpected execSQL")

  override fun execSQL(
    sql: String,
    bindArgs: Array<out Any?>
  ) = error("Unexpected execSQL")

  override val isReadOnly = false

  override val isOpen = true

  override fun needUpgrade(newVersion: Int) = false

  override val path = "test.db"

  override fun setLocale(locale: Locale) = Unit

  override fun setMaxSqlCacheSize(cacheSize: Int) = Unit

  override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = Unit

  override fun enableWriteAheadLogging() = false

  override fun disableWriteAheadLogging() = Unit

  override val isWriteAheadLoggingEnabled = false

  override val attachedDbs = emptyList<Pair<String, String>>()

  override val isDatabaseIntegrityOk = true

  override fun close() = Unit
}

internal class RecordingStatement(
  private val database: RecordingDatabase,
  val sql: String
) : SupportSQLiteStatement {
  val bindings = linkedMapOf<Int, Any?>()
  var closeCalls = 0
    private set
  var closeFailure: RuntimeException? = null

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

  override fun clearBindings() = bindings.clear()

  override fun execute() = Unit

  override fun executeUpdateDelete() = database.updateResults.removeFirstOrNull() ?: 1

  override fun executeInsert() = database.insertResults.removeFirstOrNull() ?: 1L

  override fun simpleQueryForLong() = 0L

  override fun simpleQueryForString() = ""

  override fun close() {
    closeCalls++
    closeFailure?.let { throw it }
  }
}

internal class RecordingOpenHelper(
  database: SupportSQLiteDatabase
) : SupportSQLiteOpenHelper {
  override val databaseName = "test.db"

  override fun setWriteAheadLoggingEnabled(enabled: Boolean) = Unit

  override val writableDatabase = database

  override val readableDatabase = database

  override fun close() = Unit
}

internal class TestGeneratedDatabase(
  private val tableCount: Int,
  private val submoduleTableCounts: Map<String, Int> = emptyMap()
) : GeneratedDatabase {
  val temporarySchemaDatabases: List<SupportSQLiteDatabase>
    field = mutableListOf()

  override fun configureDatabase(db: SupportSQLiteDatabase) = Unit

  override fun createSchema(db: SupportSQLiteDatabase) = Unit

  override fun createTemporarySchema(db: SupportSQLiteDatabase) {
    temporarySchemaDatabases += db
  }

  override fun clearData(db: SupportSQLiteDatabase) = MutableScatterSet<String>()

  override fun migrateViews(db: SupportSQLiteDatabase) = Unit

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
  database: SupportSQLiteDatabase,
  tableCount: Int = 1
) = DbConnectionImpl(
  database = TestGeneratedDatabase(tableCount),
  dbHelper = RecordingOpenHelper(database),
  queryScheduler = Schedulers.trampoline()
)

internal class RecordingConnection(
  val recordingDatabase: RecordingDatabase,
  tableCount: Int = 1
) {
  val connection = testDbConnection(
    database = recordingDatabase,
    tableCount = tableCount
  )
  private val recordedTriggers = connection.recordTriggers()
  val triggers: List<Set<String>> get() = recordedTriggers.values()
  val statementSql
    get() = recordingDatabase.compiledStatements.map(RecordingStatement::sql)
  val statementBindings
    get() = recordingDatabase.compiledStatements.map(RecordingStatement::bindings)
  val statementCloseCalls
    get() = recordingDatabase.compiledStatements.map(RecordingStatement::closeCalls)

  fun <R> transactionResult(result: R) = TransactionResult(
    result = result,
    committed = recordingDatabase.successfulTransactions,
    rolledBack = recordingDatabase.rolledBackTransactions
  )
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
