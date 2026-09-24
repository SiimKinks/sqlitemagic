package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.SqlUtil.bindAllArgsAsStrings
import io.reactivex.Single

/**
 * Compiled SQL UPDATE statement.
 */
class CompiledUpdate internal constructor(
  private val updateStm: SupportSQLiteStatement,
  private val tableName: String,
  private val dbConnection: DbConnectionImpl
) {
  /**
   * Execute this compiled update statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Number of updated rows
   */
  @WorkerThread
  fun execute(): Int {
    val affectedRows = synchronized(updateStm) {
      updateStm.executeUpdateDelete()
    }
    if (affectedRows > 0) {
      dbConnection.sendTableTrigger(tableName)
    }
    return affectedRows
  }

  /**
   * Creates a [Single] that when subscribed to executes this compiled
   * update statement against a database and emits nr of updated records to downstream
   * only once.
   *
   * @return Deferred [Single] that when subscribed to executes the statement and emits
   * its result to downstream
   */
  @CheckResult
  fun observe(): Single<Int> = Single.fromCallable(::execute)

  internal class Builder(
    internal var sqlTreeRoot: UpdateSqlNode? = null,
    internal var sqlNodeCount: Int = 0,
    internal var tableNode: Update.TableNode<*>? = null,
    internal val args: ArrayList<String?> = ArrayList(),
    internal var dbConnection: DbConnectionImpl? = null
  ) {
    @CheckResult
    internal fun build(): CompiledUpdate {
      val dbc = dbConnection ?: SqliteMagic.getDefaultDbConnection()
      val sql = SqlCreator.getSql(checkNotNull(sqlTreeRoot), sqlNodeCount)
      val stm = dbc.compileStatement(sql)
      bindAllArgsAsStrings(stm, args.toTypedArray())
      return CompiledUpdate(
        updateStm = stm,
        tableName = checkNotNull(tableNode).tableName,
        dbConnection = dbc
      )
    }
  }
}
