package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.Size
import androidx.annotation.WorkerThread
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation

@Suppress("UNCHECKED_CAST")
open class RawSelectNode<R, CompiledType : CompiledRawSelect> internal constructor(
  @JvmField
  internal val rawSelectBuilder: RawSelect.Builder
) : ConnectionProvidedOperation<R> {
  @CheckResult
  override fun usingConnection(connection: DbConnection): R {
    rawSelectBuilder.dbConnection = connection as DbConnectionImpl
    return this as R
  }

  /**
   * Define SQL arguments.
   *
   * @param args Arguments for the created SQL
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  fun withArgs(@Size(min = 1) vararg args: String): R {
    rawSelectBuilder.args = args
    return this as R
  }

  /**
   * Compiles this SQL SELECT statement.
   *
   * Result object is immutable and can be shared between threads without any side effects.
   *
   * Note: This method does not compile underlying SQL statement.
   *
   * @return Immutable compiled raw SQL SELECT statement
   */
  @CheckResult
  @Suppress("UNCHECKED_CAST")
  fun compile(): CompiledType = RawSelect
    .CompiledRawSelectImpl(builder = rawSelectBuilder)
      as CompiledType

  /**
   * Compile and execute this raw SELECT statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return [Cursor] over the result set
   */
  @CheckResult
  @WorkerThread
  fun execute(): Cursor = RawSelect
    .CompiledRawSelectImpl(builder = rawSelectBuilder)
    .execute()
}
