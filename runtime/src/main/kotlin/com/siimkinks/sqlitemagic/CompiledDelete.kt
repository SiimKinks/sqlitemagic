package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.SqlUtil.bindAllArgsAsStrings
import io.reactivex.Single

/**
 * Compiled SQL DELETE statement.
 */
class CompiledDelete internal constructor(
  private val deleteStm: SupportSQLiteStatement,
  private val tableName: String,
  private val dbConnection: DbConnectionImpl
) {
  /**
   * Execute this compiled delete statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Number of deleted rows
   */
  @WorkerThread
  fun execute(): Int {
    val affectedRows = synchronized(deleteStm) {
      deleteStm.executeUpdateDelete()
    }
    if (affectedRows > 0) {
      dbConnection.sendTableTrigger(tableName)
    }
    return affectedRows
  }

  /**
   * Creates a [Single] that when subscribed to executes this compiled
   * delete statement against a database and emits nr of deleted records to downstream
   * only once.
   *
   * @return Deferred [Single] that when subscribed to executes the statement and emits
   * its result to downstream
   */
  @CheckResult
  fun observe(): Single<Int> = Single.fromCallable(::execute)

  internal class Builder(
    internal var sqlTreeRoot: DeleteSqlNode? = null,
    internal var sqlNodeCount: Int = 0,
    internal var from: Delete.From? = null,
    internal val args: ArrayList<String?> = ArrayList(),
    internal var dbConnection: DbConnectionImpl? = null
  ) {
    @CheckResult
    internal fun build(): CompiledDelete {
      val sql = SqlCreator.getSql(checkNotNull(sqlTreeRoot), sqlNodeCount)
      val dbConnection = dbConnection ?: SqliteMagic.getDefaultDbConnection()
      val stm = dbConnection.compileStatement(sql)
      bindAllArgsAsStrings(stm, args.toTypedArray())
      return CompiledDelete(
        deleteStm = stm,
        tableName = checkNotNull(from).tableName,
        dbConnection = dbConnection
      )
    }
  }
}
