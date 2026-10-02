package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteTransactionListener
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

/**
 * Note: some parts are forked from [sqlbrite](https://github.com/square/sqlbrite) and
 * modified to extended functionality and improvements.
 */
internal class DbConnectionImpl(
  private val database: GeneratedDatabase,
  private val dbHelper: SupportSQLiteOpenHelper,
  internal val queryScheduler: Scheduler
) : DbConnection {
  private val transactions = ThreadLocal<SqliteTransaction?>()

  /** Publishes sets of tables which have changed. */
  internal val triggers: Observable<Set<String>>
    field = PublishSubject.create()

  private val transactionHandle = object : Transaction {
    override fun markSuccessful() {
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN SUCCESS %s", transactions.get())
      writableDatabase.setTransactionSuccessful()
    }

    override fun yieldIfContendedSafely() = writableDatabase.yieldIfContendedSafely()

    override fun yieldIfContendedSafely(
      sleepAmount: Long,
      sleepUnit: TimeUnit
    ) = writableDatabase.yieldIfContendedSafely(sleepUnit.toMillis(sleepAmount))

    override fun end() {
      val transactionState = checkNotNull(transactions.get()) { "Not in transaction." }
      transactions.set(transactionState.parent)
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN END %s", transactionState)
      writableDatabase.endTransaction()
      // Send the triggers after ending the transaction in the DB.
      if (transactionState.commit && transactionState.isNotEmpty()) {
        sendTableTriggers(transactionState)
      }
    }

    override fun close() = end()
  }

  internal val ensureNotInTransaction = Consumer<Any> {
    check(!hasActiveTransaction) {
      "Cannot subscribe to observable query in a transaction."
    }
  }

  private val submoduleEntityDbManagers = database
    .submoduleNames
    ?.let { submoduleNames ->
      SimpleArrayMap<String, Array<EntityDbManager?>>(submoduleNames.size)
        .apply {
          for (submoduleName in submoduleNames) {
            put(submoduleName, createEntityDbManagers(submoduleName))
          }
        }
    }
  private val entityDbManagers = createEntityDbManagers("")

  private fun createEntityDbManagers(moduleName: String) = Array<EntityDbManager?>(
    size = database.getNrOfTables(moduleName),
    init = { EntityDbManager(this) }
  )

  override fun close() {
    if (triggers.hasComplete()) {
      return
    }
    triggers.onComplete()
    closeEntityDbManagers(entityDbManagers)
    submoduleEntityDbManagers?.let { managers ->
      for (index in 0 until managers.size()) {
        closeEntityDbManagers(managers.valueAt(index))
      }
    }
    dbHelper.close()
    LogUtil.logInfo("Closed database [name=%s]", dbHelper.databaseName)
  }

  private fun closeEntityDbManagers(managers: Array<EntityDbManager?>) {
    for (index in managers.indices) {
      managers[index]?.close()
      managers[index] = null
    }
  }

  @CheckResult
  override fun newTransaction(): Transaction {
    val transactionState = SqliteTransaction(transactions.get())
    transactions.set(transactionState)
    if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN BEGIN %s", transactionState)
    writableDatabase.beginTransactionWithListener(transactionState)
    return transactionHandle
  }

  override fun clearData() {
    database
      .clearData(writableDatabase)
      ?.let(::sendTableTriggers)
  }

  internal val readableDatabase get() = dbHelper.readableDatabase

  internal val writableDatabase get() = dbHelper.writableDatabase

  internal val hasActiveTransaction get() = transactions.get() != null

  @CheckResult
  internal fun getEntityDbManager(
    moduleName: String?,
    tablePos: Int
  ): EntityDbManager {
    val managers = when (moduleName) {
      null -> entityDbManagers
      else -> checkNotNull(submoduleEntityDbManagers?.get(moduleName)) {
        "No submodules configured"
      }
    }
    return checkNotNull(managers[tablePos]) { "DB connection closed" }
  }

  internal fun compileStatement(sql: String) = writableDatabase.compileStatement(sql)

  internal fun sendTableTrigger(table: String) {
    when (val transactionState = transactions.get()) {
      null -> publishTableTriggers(setOf(table))
      else -> transactionState.add(table)
    }
  }

  internal fun sendTableTriggers(vararg tables: String) {
    when (val transactionState = transactions.get()) {
      null -> publishTableTriggers(StringArraySet(tables))
      else -> transactionState.addAll(tables)
    }
  }

  private fun sendTableTriggers(tables: StringArraySet) {
    when (val transactionState = transactions.get()) {
      null -> publishTableTriggers(tables)
      else -> transactionState.addAll(tables)
    }
  }

  private fun publishTableTriggers(tables: Set<String>) {
    if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TRIGGER %s", tables)
    triggers.onNext(tables)
  }

  private class SqliteTransaction(
    val parent: SqliteTransaction?
  ) : StringArraySet(), SQLiteTransactionListener {
    var commit = false
      private set

    override fun onBegin() = Unit

    override fun onCommit() {
      commit = true
    }

    override fun onRollback() = Unit

    override fun toString(): String {
      val name = "%08x".format(System.identityHashCode(this))
      return when (parent) {
        null -> name
        else -> "$name [$parent]"
      }
    }
  }
}
