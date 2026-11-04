package com.siimkinks.sqlitemagic.framework

import android.database.DatabaseUtils.STATEMENT_ABORT
import android.database.DatabaseUtils.STATEMENT_BEGIN
import android.database.DatabaseUtils.STATEMENT_COMMIT
import android.database.DatabaseUtils.STATEMENT_OTHER
import android.database.DatabaseUtils.getSqlStatementType
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.siimkinks.sqlitemagic.framework.TransactionControl.BEGIN
import com.siimkinks.sqlitemagic.framework.TransactionControl.END
import com.siimkinks.sqlitemagic.framework.TransactionControl.NONE
import com.siimkinks.sqlitemagic.framework.TransactionControl.RELEASE
import com.siimkinks.sqlitemagic.framework.TransactionControl.ROLLBACK_TO
import com.siimkinks.sqlitemagic.framework.TransactionControl.SAVEPOINT
import kotlin.text.RegexOption.IGNORE_CASE
import android.database.sqlite.SQLiteStatement as FrameworkProgram

internal class TransactionState(private val db: SQLiteDatabase) {
  var isOpen = false
    private set
  private var openedByBegin = false

  fun execute(
    program: FrameworkProgram,
    control: TransactionControl
  ) {
    guard(control) { program.execute() }
    when (control) {
      BEGIN -> {
        isOpen = true
        openedByBegin = true
      }
      SAVEPOINT -> isOpen = true
      END -> {
        isOpen = false
        openedByBegin = false
      }
      RELEASE -> if (!openedByBegin) {
        isOpen = probe()
      }
      NONE, ROLLBACK_TO -> Unit
    }
  }

  inline fun <T> guard(
    control: TransactionControl = NONE,
    block: () -> T
  ): T = try {
    block()
  } catch (failure: Throwable) {
    if (isOpen || control != NONE) {
      resync(failure)
    }
    throw failure
  }

  fun resync(failure: Throwable) {
    val begun = openedByBegin
    openedByBegin = false
    try {
      isOpen = probe()
      openedByBegin = begun && isOpen
    } catch (probeFailure: Throwable) {
      failure.addSuppressed(probeFailure)
    }
  }

  private fun probe() = try {
    execute(PROBE_BEGIN)
    execute(PROBE_ROLLBACK)
    false
  } catch (failure: SQLiteException) {
    if (failure.message?.contains(NESTED_BEGIN) != true) {
      throw failure
    }
    true
  }

  private fun execute(sql: String) = db
    .compileStatement(sql)
    .use(FrameworkProgram::execute)
}

internal enum class TransactionControl { NONE, BEGIN, END, ROLLBACK_TO, SAVEPOINT, RELEASE }

internal fun transactionControlOf(sql: String) = when (getSqlStatementType(sql)) {
  STATEMENT_BEGIN -> BEGIN
  STATEMENT_COMMIT -> END
  STATEMENT_ABORT -> when {
    SAVEPOINT_TARGET.containsMatchIn(sql) -> ROLLBACK_TO
    else -> END
  }
  STATEMENT_OTHER -> when {
    SAVEPOINT_COMMAND.containsMatchIn(sql) -> SAVEPOINT
    RELEASE_COMMAND.containsMatchIn(sql) -> RELEASE
    else -> NONE
  }
  else -> NONE
}

internal fun sessionBypassing(
  sql: String,
  control: TransactionControl
) = when (control) {
  BEGIN, END, ROLLBACK_TO -> "$SESSION_BYPASS$sql"
  NONE, SAVEPOINT, RELEASE -> sql
}

private const val SESSION_BYPASS = ";"
private const val PROBE_BEGIN = "${SESSION_BYPASS}BEGIN DEFERRED"
private const val PROBE_ROLLBACK = "${SESSION_BYPASS}ROLLBACK"
private const val NESTED_BEGIN = "cannot start a transaction within a transaction"
private val SAVEPOINT_TARGET = Regex(
  pattern = """\bTO\b""",
  option = IGNORE_CASE
)
private val SAVEPOINT_COMMAND = Regex(
  pattern = """^\s*SAVEPOINT\b""",
  option = IGNORE_CASE
)
private val RELEASE_COMMAND = Regex(
  pattern = """^\s*RELEASE\b""",
  option = IGNORE_CASE
)
