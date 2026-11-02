package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class TransactionBlockContractTest {
  @Test
  fun `a transaction block returns to inTransaction with a label`() {
    compileTransactionConsumer(
      """
        fun verify(connection: DbConnection, skip: Boolean) {
          inTransaction {
            if (skip) return@inTransaction
          }
          connection.inTransaction {
            if (skip) return@inTransaction
          }
        }
      """
    )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `a transaction block can neither suspend nor leave the enclosing function`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileTransactionConsumer(
      """
        suspend fun pause() = Unit

        suspend fun verify(connection: DbConnection, items: List<String>) {
          $program
        }
      """
    )
      .assertCompilationError(diagnostic)
  }

  private fun compileTransactionConsumer(body: String) = compileExpressionConsumer(
    body = body,
    imports = """
      import com.siimkinks.sqlitemagic.inTransaction
    """
  )

  companion object {
    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "suspend call in the default connection's block",
        "inTransaction { pause() }",
        "Suspension functions can only be called within coroutine body"
      ),
      Arguments.of(
        "suspend call in a connection's block",
        "connection.inTransaction { pause() }",
        "Suspension functions can only be called within coroutine body"
      ),
      Arguments.of(
        "return from the enclosing function",
        "inTransaction { return }",
        "'return' is prohibited here"
      ),
      Arguments.of(
        "break out of an enclosing loop",
        "for (item in items) { connection.inTransaction { break } }",
        "'break' or 'continue' crosses a function or class boundary"
      )
    )
  }
}
