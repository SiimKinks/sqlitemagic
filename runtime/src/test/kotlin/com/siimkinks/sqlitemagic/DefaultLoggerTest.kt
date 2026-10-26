package com.siimkinks.sqlitemagic

import android.util.Log
import org.junit.Test
import org.mockito.Mockito.mockStatic

internal class DefaultLoggerTest {
  @Test
  fun `database messages retain their Android levels tag prefix and throwable`() {
    val logger = DefaultLogger()
    val error = IllegalArgumentException("invalid query")
    mockStatic(Log::class.java).use { androidLog ->
      logger.logDebug("debug")
      logger.logError(
        message = "failure",
        throwable = error
      )

      androidLog.verify { Log.d("SqliteMagic", "DATABASE: debug") }
      androidLog.verify { Log.e("SqliteMagic", "DATABASE: failure", error) }
      androidLog.verifyNoMoreInteractions()
    }
  }
}
