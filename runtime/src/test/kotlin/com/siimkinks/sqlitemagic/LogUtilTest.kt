package com.siimkinks.sqlitemagic

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.mockito.Mockito.mockStatic

internal class LogUtilTest {
  @Test
  fun `custom logger messages format supplied values and preserve unformatted percent signs`() {
    val cases = listOf(
        MessageCase(
            label = "no arguments",
            message = "progress 50% and literal %s",
            args = emptyArray(),
            expected = "progress 50% and literal %s"
        ),
        MessageCase(
            label = "multiple arguments",
            message = "%s affected %d rows",
            args = arrayOf<Any?>("UPDATE", 3),
            expected = "UPDATE affected 3 rows"
        ),
        MessageCase(
            label = "null argument",
            message = "value: %s",
            args = arrayOf(null),
            expected = "value: null"
        )
    )
    for (case in cases) {
      withLogger { logger ->
        LogUtil.logDebug(case.message, *case.args)
        LogUtil.logWarning(case.message, *case.args)
        LogUtil.logError(case.message, *case.args)

        assertWithMessage(case.label)
            .that(logger.events)
            .containsExactly(
                LoggedMessage(
                    level = LogLevel.DEBUG,
                    message = case.expected
                ),
                LoggedMessage(
                    level = LogLevel.WARNING,
                    message = case.expected
                ),
                LoggedMessage(
                    level = LogLevel.ERROR,
                    message = case.expected
                )
            )
            .inOrder()
      }
    }
  }

  @Test
  fun `exception first overload delivers the original throwable and message only overload formats it`() {
    withLogger { logger ->
      val error = IllegalStateException("database closed")

      LogUtil.logError(error, "failed %s on row %d", "UPDATE", 7)
      LogUtil.logError("reported %s", error)

      assertThat(logger.events)
          .containsExactly(
              LoggedMessage(
                  level = LogLevel.ERROR,
                  message = "failed UPDATE on row 7",
                  throwable = error
              ),
              LoggedMessage(
                  level = LogLevel.ERROR,
                  message = "reported java.lang.IllegalStateException: database closed"
              )
          )
          .inOrder()
    }
  }

  @Test
  fun `query details are forwarded including absent and empty arguments`() {
    val cases = listOf(
        QueryArgsCase(
            label = "absent arguments",
            args = null,
            expected = null
        ),
        QueryArgsCase(
            label = "empty arguments",
            args = emptyArray(),
            expected = emptyList()
        ),
        QueryArgsCase(
            label = "populated arguments",
            args = arrayOf("author-1", "book-2"),
            expected = listOf("author-1", "book-2")
        ),
        QueryArgsCase(
            label = "null argument element",
            args = arrayOf("author-1", null),
            expected = listOf("author-1", null)
        )
    )
    for (case in cases) {
      withLogger { logger ->
        LogUtil.logQueryTime(
            23L,
            arrayOf("books", "authors"),
            "SELECT * FROM books WHERE author = ? AND id = ?",
            case.args
        )

        assertWithMessage(case.label)
            .that(logger.events)
            .containsExactly(
                LoggedQuery(
                    queryTimeInMillis = 23L,
                    observedTables = listOf("books", "authors"),
                    sql = "SELECT * FROM books WHERE author = ? AND id = ?",
                    args = case.expected
                )
            )
      }
    }
  }

  @Test
  fun `informational and mandatory warning messages use Android logging regardless of logging gate`() {
    withLogger { logger ->
      SqliteMagic.LOGGING_ENABLED = false
      mockStatic(Log::class.java).use { androidLog ->
        LogUtil.logInfo("progress 50%")
        LogUtil.logInfo("opened %s version %d", "books", 2)
        LogUtil.logMandatoryWarning("literal %s")
        LogUtil.logMandatoryWarning("missing %s", null)

        androidLog.verify { Log.i("SqliteMagic", "progress 50%") }
        androidLog.verify { Log.i("SqliteMagic", "opened books version 2") }
        androidLog.verify { Log.w("SqliteMagic", "literal %s") }
        androidLog.verify { Log.w("SqliteMagic", "missing null") }
        androidLog.verifyNoMoreInteractions()
      }
      assertThat(logger.events).isEmpty()
    }
  }

  private fun withLogger(block: (RecordingLogger) -> Unit) {
    val previousLogger = SqliteMagic.LOGGER
    val previousLoggingEnabled = SqliteMagic.LOGGING_ENABLED
    val logger = RecordingLogger()
    SqliteMagic.setLogger(logger)
    try {
      block(logger)
    } finally {
      SqliteMagic.LOGGER = previousLogger
      SqliteMagic.LOGGING_ENABLED = previousLoggingEnabled
    }
  }

  private class RecordingLogger : Logger {
    val events: List<LoggedEvent>
      field = mutableListOf()

    override fun logDebug(message: String) {
      events += LoggedMessage(
          level = LogLevel.DEBUG,
          message = message
      )
    }

    override fun logWarning(message: String) {
      events += LoggedMessage(
          level = LogLevel.WARNING,
          message = message
      )
    }

    override fun logError(message: String) {
      events += LoggedMessage(
          level = LogLevel.ERROR,
          message = message
      )
    }

    override fun logError(message: String, throwable: Throwable) {
      events += LoggedMessage(
          level = LogLevel.ERROR,
          message = message,
          throwable = throwable
      )
    }

    override fun logQueryTime(
        queryTimeInMillis: Long,
        observedTables: Array<String>,
        sql: String,
        args: Array<out String?>?
    ) {
      events += LoggedQuery(
          queryTimeInMillis = queryTimeInMillis,
          observedTables = observedTables.toList(),
          sql = sql,
          args = args?.toList()
      )
    }
  }

  private class MessageCase(
      val label: String,
      val message: String,
      val args: Array<out Any?>,
      val expected: String
  )

  private sealed interface LoggedEvent

  private enum class LogLevel {
    DEBUG,
    WARNING,
    ERROR
  }

  private data class LoggedMessage(
      val level: LogLevel,
      val message: String,
      val throwable: Throwable? = null
  ) : LoggedEvent

  private data class LoggedQuery(
      val queryTimeInMillis: Long,
      val observedTables: List<String>,
      val sql: String,
      val args: List<String?>?
  ) : LoggedEvent

  private class QueryArgsCase(
      val label: String,
      val args: Array<out String?>?,
      val expected: List<String?>?
  )
}
