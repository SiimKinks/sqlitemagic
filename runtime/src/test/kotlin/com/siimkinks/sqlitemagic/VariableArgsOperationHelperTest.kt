package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class VariableArgsOperationHelperTest {
  @Test
  fun `statement SQL follows changing tables columns and operation kinds`() {
    val connection = newConnection()
    val context = operationContext(
      connection = connection,
      conflictAlgorithm = CONFLICT_IGNORE
    )
    val values = context.bindValues.apply {
      this["b"] = "second"
      this["a"] = "first"
    }
    context.compileInsertStatement("books")
    values.remove("b")
    context.compileInsertStatement("authors")
    context.compileUpdateStatement(
      tableName = "books",
      resolutionColumn = "key",
      resolutionValue = "book-key"
    )
    values["b"] = 2
    context.compileUpdateStatement(
      tableName = "authors",
      resolutionColumn = "id",
      resolutionValue = 9L
    )
    context.compileInsertStatement("books")

    data class ExpectedStatement(
      val prefix: String,
      val suffix: String,
      val values: Map<String, Any>
    )

    val expectedStatements = listOf(
      ExpectedStatement(
        prefix = "INSERT OR IGNORE INTO books",
        suffix = ") VALUES (?,?)",
        values = mapOf("a" to "first", "b" to "second")
      ),
      ExpectedStatement(
        prefix = "INSERT OR IGNORE INTO authors",
        suffix = ") VALUES (?)",
        values = mapOf("a" to "first")
      ),
      ExpectedStatement(
        prefix = "UPDATE OR IGNORE books",
        suffix = " WHERE key=?",
        values = mapOf("a" to "first")
      ),
      ExpectedStatement(
        prefix = "UPDATE OR IGNORE authors",
        suffix = " WHERE id=?",
        values = mapOf("a" to "first", "b" to 2L)
      ),
      ExpectedStatement(
        prefix = "INSERT OR IGNORE INTO books",
        suffix = ") VALUES (?,?)",
        values = mapOf("a" to "first", "b" to 2L)
      )
    )
    val statements = connection.recordingDatabase.compiledStatements
    assertThat(statements).hasSize(expectedStatements.size)
    statements.zip(expectedStatements).forEach { (statement, expected) ->
      assertThat(statement.sql).startsWith(expected.prefix)
      assertThat(statement.sql).endsWith(expected.suffix)
      assertBoundColumns(
        statement = statement,
        expected = expected.values
      )
    }
    assertThat(connection.statementBindings[2][2]).isEqualTo("book-key")
    assertThat(connection.statementBindings[3][3]).isEqualTo(9L)
  }

  @Test
  fun `native traversal aligns columns and bindings after collisions growth and repeated refill`() {
    val connection = newConnection()
    val context = operationContext(connection)
    val values = context.bindValues
    val collidingColumns = listOf("Aa", "BB").flatMap { first ->
      listOf("Aa", "BB").flatMap { second ->
        listOf("Aa", "BB").flatMap { third ->
          listOf("Aa", "BB").map { fourth -> first + second + third + fourth }
        }
      }
    }
    repeat(3) { refill ->
      values.clear()
      collidingColumns.forEachIndexed { index, column -> values[column] = index.toLong() + refill }
      collidingColumns.take(4).forEach(values::remove)
      values[collidingColumns.first()] = 99L
      val expected = collidingColumns.drop(4)
        .associateWith { collidingColumns.indexOf(it).toLong() + refill } + (collidingColumns.first() to 99L)
      context.compileInsertStatement("books")
      assertBoundColumns(
        statement = connection.recordingDatabase.compiledStatements.last(),
        expected = expected
      )
      context.compileUpdateStatement(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = 5L
      )
      val statement = connection.recordingDatabase.compiledStatements.last()
      assertBoundColumns(
        statement = statement,
        expected = expected
      )
      assertThat(statement.bindings[values.size + 1]).isEqualTo(5L)
    }
  }

  @Test
  fun `values and explicit resolution identities use their SQLite binding types`() {
    data class BindingCase(
      val label: String,
      val value: Any,
      val expected: Any
    )

    listOf(
      BindingCase(
        label = "string",
        value = "hello",
        expected = "hello"
      ),
      BindingCase(
        label = "byte",
        value = 3.toByte(),
        expected = 3L
      ),
      BindingCase(
        label = "short",
        value = 4.toShort(),
        expected = 4L
      ),
      BindingCase(
        label = "integer",
        value = 5,
        expected = 5L
      ),
      BindingCase(
        label = "long",
        value = 6L,
        expected = 6L
      ),
      BindingCase(
        label = "float",
        value = 1.5f,
        expected = 1.5
      ),
      BindingCase(
        label = "double",
        value = 2.5,
        expected = 2.5
      ),
      BindingCase(
        label = "blob",
        value = byteArrayOf(1, 2),
        expected = byteArrayOf(1, 2)
      )
    ).forEach { case ->
      val connection = newConnection()
      val context = operationContext(connection)
      context.bindValues["value"] = case.value
      context.compileUpdateStatement(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = case.value
      )
      val statement = connection.recordingDatabase.compiledStatements.single()

      assertWithMessage(case.label)
        .that(statement.sql)
        .isEqualTo("UPDATE books SET value=? WHERE id=?")
      for (index in 1..2) {
        assertWithMessage("${case.label}: binding $index")
          .that(statement.bindings[index])
          .isEqualTo(case.expected)
      }
    }
  }

  private fun operationContext(
    connection: RecordingConnection,
    conflictAlgorithm: Int = CONFLICT_NONE
  ) = OperationContext(
    adapter = TestAdapter(),
    configuration = OperationConfigurationSnapshot(
      connection = connection.connection,
      conflictAlgorithm = conflictAlgorithm
    )
  )
}

private fun assertBoundColumns(
  statement: RecordingStatement,
  expected: Map<String, Any>
) {
  val columns = when {
    statement.sql.startsWith("INSERT") -> statement.sql
      .substringAfter('(')
      .substringBefore(')')
      .split(',')
    else -> statement.sql
      .substringAfter(" SET ")
      .substringBefore(" WHERE ")
      .split(',')
      .map { it.removeSuffix("=?") }
  }
  assertThat(columns).containsExactlyElementsIn(expected.keys)
  columns.forEachIndexed { index, column ->
    assertWithMessage("${statement.sql}: $column")
      .that(statement.bindings[index + 1])
      .isEqualTo(expected.getValue(column))
  }
}
