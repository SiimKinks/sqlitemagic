package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ConnectionStatementTest {
  @Test
  fun `explicit connection compiles and binds eagerly`() {
    val connection = mockConnection()
    val statement = mockStatement()
    whenever(connection.compileStatement(SQL)).thenReturn(statement)

    ConnectionStatement(
      sql = SQL,
      args = ARGS,
      initialConnection = connection
    )

    verify(connection).compileStatement(SQL)
    verify(statement).bindString(2, "second")
    verify(statement).bindString(1, "first")
  }

  @Test
  fun `unspecified connection compiles lazily and reuses the statement`() {
    val connection = mockConnection()
    val statement = mockStatement()
    whenever(connection.compileStatement(SQL)).thenReturn(statement)
    whenever(statement.simpleQueryForLong()).thenReturn(7L)
    val connectionStatement = ConnectionStatement(
      sql = SQL,
      args = ARGS,
      initialConnection = null
    )

    verify(connection, never()).compileStatement(SQL)
    val firstResult = connectionStatement.execute(
      dbConnection = connection,
      operation = SupportSQLiteStatement::simpleQueryForLong
    )
    val secondResult = connectionStatement.execute(
      dbConnection = connection,
      operation = SupportSQLiteStatement::simpleQueryForLong
    )

    assertThat(listOf(firstResult, secondResult)).containsExactly(7L, 7L).inOrder()
    verify(connection).compileStatement(SQL)
    verify(statement).bindString(2, "second")
    verify(statement).bindString(1, "first")
    verify(statement, times(2)).simpleQueryForLong()
  }

  @Test
  fun `statement execution is serialized`() {
    val connection = mockConnection()
    val statement = mockStatement()
    whenever(connection.compileStatement(SQL)).thenReturn(statement)
    val connectionStatement = ConnectionStatement(
      sql = SQL,
      args = null,
      initialConnection = connection
    )
    val firstEntered = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val secondEntered = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val first = executor.submit {
        connectionStatement.execute(
          dbConnection = connection,
          operation = {
            firstEntered.countDown()
            check(releaseFirst.await(1, TimeUnit.SECONDS)) { "Timed out waiting to release first execution" }
          }
        )
      }
      assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue()

      val second = executor.submit {
        secondStarted.countDown()
        connectionStatement.execute(
          dbConnection = connection,
          operation = {
            secondEntered.countDown()
          }
        )
      }
      assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue()
      assertThat(secondEntered.await(100, TimeUnit.MILLISECONDS)).isFalse()

      releaseFirst.countDown()
      first.get(1, TimeUnit.SECONDS)
      second.get(1, TimeUnit.SECONDS)
      assertThat(secondEntered.await(1, TimeUnit.SECONDS)).isTrue()
    } finally {
      releaseFirst.countDown()
      executor.shutdownNow()
      assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue()
    }
  }

  private fun mockConnection() = mock<DbConnectionImpl>()

  private fun mockStatement() = mock<SupportSQLiteStatement>()

  companion object {
    private const val SQL = "SELECT count(*) FROM model"
    private val ARGS: Array<String?> = arrayOf("first", "second")
  }
}
