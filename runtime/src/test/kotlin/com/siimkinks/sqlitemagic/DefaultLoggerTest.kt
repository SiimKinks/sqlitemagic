package com.siimkinks.sqlitemagic

import android.util.Log
import org.junit.Test
import org.mockito.Mockito.description
import org.mockito.Mockito.mockStatic

internal class DefaultLoggerTest {
  @Test
  fun `database messages retain their Android levels tag prefix and throwable`() {
    val logger = DefaultLogger()
    val error = IllegalArgumentException("invalid query")
    mockStatic(Log::class.java).use { androidLog ->
      logger.logDebug("debug")
      logger.logWarning("warning")
      logger.logError("error")
      logger.logError("failure", error)

      androidLog.verify { Log.d("SqliteMagic", "DATABASE: debug") }
      androidLog.verify { Log.w("SqliteMagic", "DATABASE: warning") }
      androidLog.verify { Log.e("SqliteMagic", "DATABASE: error") }
      androidLog.verify { Log.e("SqliteMagic", "DATABASE: failure", error) }
      androidLog.verifyNoMoreInteractions()
    }
  }

  @Test
  fun `query messages retain timing tables SQL and distinguish absent from empty arguments`() {
    val logger = DefaultLogger()
    val cases = listOf(
      QueryArgsCase(
        label = "absent arguments",
        args = null,
        expected = "null"
      ),
      QueryArgsCase(
        label = "empty arguments",
        args = emptyArray(),
        expected = "[]"
      ),
      QueryArgsCase(
        label = "populated arguments",
        args = arrayOf("author-1", "book-2"),
        expected = "[author-1, book-2]"
      ),
      QueryArgsCase(
        label = "null argument element",
        args = arrayOf("author-1", null),
        expected = "[author-1, null]"
      )
    )
    for (case in cases) {
      mockStatic(Log::class.java).use { androidLog ->
        logger.logQueryTime(
          23L,
          arrayOf("books", "authors"),
          "SELECT * FROM books WHERE author = ? AND id = ?",
          case.args
        )

        val expectedMessage = "DATABASE:  QUERY (23ms)\n" +
            "  tables: [books, authors]\n" +
            "  sql: SELECT * FROM books WHERE author = ? AND id = ?\n" +
            "  args: ${case.expected}"
        androidLog.verify({ Log.d("SqliteMagic", expectedMessage) }, description(case.label))
        androidLog.verifyNoMoreInteractions()
      }
    }
  }

  private class QueryArgsCase(
    val label: String,
    val args: Array<out String?>?,
    val expected: String
  )
}
