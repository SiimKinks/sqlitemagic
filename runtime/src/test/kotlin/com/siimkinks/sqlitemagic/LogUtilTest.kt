package com.siimkinks.sqlitemagic

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.LogUtil
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import java.util.concurrent.CancellationException

internal class LogUtilTest {
  @Test
  fun `debug and error messages reach the custom logger only while logging is enabled`() {
    withLogger(loggingEnabled = false) { logger ->
      val error = IllegalStateException("database closed")
      var evaluated = 0

      LogUtil.logDebug {
        evaluated++
        "skipped"
      }
      LogUtil.logError(error) {
        evaluated++
        "skipped"
      }
      SqliteMagic.loggingEnabled = true
      LogUtil.logDebug { "UPDATE affected 3 rows" }
      LogUtil.logError(error) { "failed UPDATE on row 7" }

      assertThat(evaluated).isEqualTo(0)
      assertThat(logger.events)
        .containsExactly(
          LoggedMessage(
            level = LogLevel.DEBUG,
            message = "UPDATE affected 3 rows"
          ),
          LoggedMessage(
            level = LogLevel.ERROR,
            message = "failed UPDATE on row 7",
            throwable = error
          )
        )
        .inOrder()
    }
  }

  @Test
  fun `failures are logged and rethrown while cancellations pass unlogged`() {
    withLogger(loggingEnabled = true) { logger ->
      val failure = IllegalStateException("boom")
      val cancellation = CancellationException()

      assertThat(
        listOf(failure, cancellation).map { exception ->
          assertThrows(Exception::class.java) {
            LogUtil.loggingFailure(message = "Operation failed") { throw exception }
          }
        }
      ).containsExactly(failure, cancellation).inOrder()
      assertThat(logger.events).containsExactly(
        LoggedMessage(
          level = LogLevel.ERROR,
          message = "Operation failed",
          throwable = failure
        )
      )
    }
  }

  @Test
  fun `informational messages use Android logging regardless of logging gate`() {
    withLogger(loggingEnabled = false) { logger ->
      mockStatic(Log::class.java).use { androidLog ->
        LogUtil.logInfo("progress 50%")

        androidLog.verify { Log.i("SqliteMagic", "progress 50%") }
        androidLog.verifyNoMoreInteractions()
      }
      assertThat(logger.events).isEmpty()
    }
  }
}
