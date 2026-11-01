package com.siimkinks.sqlitemagic.runtime.support

import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.runsOnBundledSqlite
import org.junit.Assert.assertThrows

internal fun Throwable.isConstraintFailure() = when {
  runsOnBundledSqlite -> this is SQLException && bundledPrimaryResultCode() == SQLITE_CONSTRAINT
  else -> this is SQLiteConstraintException
}

internal fun assertConstraintFailure(operation: () -> Any?): SQLException {
  val failure = assertThrows(SQLException::class.java, operation::invoke)
  assertWithMessage("constraint failure: $failure")
    .that(failure.isConstraintFailure())
    .isTrue()
  return failure
}

private fun Throwable.bundledPrimaryResultCode() = message
  ?.let(BUNDLED_RESULT_CODE::find)
  ?.groupValues
  ?.get(1)
  ?.toInt()
  ?.and(PRIMARY_RESULT_CODE_MASK)

private val BUNDLED_RESULT_CODE = Regex("""^Error code: (\d+),""")
private const val PRIMARY_RESULT_CODE_MASK = 0xff
private const val SQLITE_CONSTRAINT = 19
