package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import io.reactivex.Single
import java.util.LinkedList

abstract class DeleteSqlNode internal constructor(
  parent: DeleteSqlNode?
) : SqlNode(parent) {
  internal val deleteBuilder: CompiledDelete.Builder =
    (parent?.deleteBuilder ?: CompiledDelete.Builder())
      .also {
        it.sqlTreeRoot = this
        it.sqlNodeCount++
      }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) = throw UnsupportedOperationException()

  abstract class DeleteNode internal constructor(
    parent: DeleteSqlNode
  ) : DeleteSqlNode(parent),
    ConnectionProvidedOperation<DeleteNode> {
    override fun usingConnection(connection: DbConnection) = apply {
      deleteBuilder.dbConnection = connection as DbConnectionImpl
    }

    /**
     * Compiles this SQL DELETE statement.
     *
     * Result object is immutable and can be shared between threads without any side effects.
     *
     * @return Immutable compiled delete statement
     */
    @CheckResult
    fun compile(): CompiledDelete = deleteBuilder.build()

    /**
     * Compile and execute this delete statement against a database.
     *
     * This method runs synchronously in the calling thread.
     *
     * @return Number of deleted rows
     */
    @WorkerThread
    fun execute(): Int = deleteBuilder
      .build()
      .execute()

    /**
     * Compiles this delete statement and creates a [Single] that when subscribed to
     * executes this statement against a database and emits nr of deleted records to downstream
     * only once.
     *
     * @return Deferred [Single] that when subscribed to executes the statement and emits
     * its result to downstream
     */
    @CheckResult
    fun observe(): Single<Int> = deleteBuilder
      .build()
      .observe()
  }
}
