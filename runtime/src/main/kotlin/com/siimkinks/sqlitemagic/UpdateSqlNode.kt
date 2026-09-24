package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import io.reactivex.Single
import java.util.LinkedList

abstract class UpdateSqlNode internal constructor(
  parent: UpdateSqlNode?
) : SqlNode(parent) {
  internal val updateBuilder: CompiledUpdate.Builder =
    (parent?.updateBuilder ?: CompiledUpdate.Builder())
      .also {
        it.sqlTreeRoot = this
        it.sqlNodeCount++
      }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    throw UnsupportedOperationException()
  }

  abstract class UpdateNode internal constructor(
    parent: UpdateSqlNode
  ) : UpdateSqlNode(parent),
    ConnectionProvidedOperation<UpdateNode> {
    override fun usingConnection(connection: DbConnection) = apply {
      updateBuilder.dbConnection = connection as DbConnectionImpl
    }

    /**
     * Compiles this SQL UPDATE statement.
     *
     * Result object is immutable and can be shared between threads without any side effects.
     *
     * @return Immutable compiled update statement
     */
    @CheckResult
    fun compile() = updateBuilder.build()

    /**
     * Compile and execute this update statement against a database.
     *
     * This method runs synchronously in the calling thread.
     *
     * @return Number of updated rows
     */
    @WorkerThread
    fun execute() = updateBuilder.build().execute()

    /**
     * Compiles this update statement and creates a [Single] that when subscribed to
     * executes this statement against a database and emits nr of updated records to downstream
     * only once.
     *
     * @return Deferred [Single] that when subscribed to executes the statement and emits
     * its result to downstream
     */
    @CheckResult
    fun observe() = updateBuilder.build().observe()
  }
}
