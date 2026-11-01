package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
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
    context.executeBoundInsert("books")
    values.remove("b")
    context.executeBoundInsert("authors")
    context.executeBoundUpdate(
      tableName = "books",
      resolutionColumn = "key",
      resolutionValue = "book-key"
    )
    values["b"] = 2
    context.executeBoundUpdate(
      tableName = "authors",
      resolutionColumn = "id",
      resolutionValue = 9L
    )
    context.executeBoundInsert("books")

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
    val executions = connection.sqliteConnection.executions
    assertThat(executions).hasSize(expectedStatements.size)
    executions
      .zip(expectedStatements)
      .forEach { (execution, expected) ->
        assertThat(execution.sql).startsWith(expected.prefix)
        assertThat(execution.sql).endsWith(expected.suffix)
        assertBoundColumns(
          execution = execution,
          expected = expected.values
        )
      }
    assertThat(executions[2].bindings[2]).isEqualTo("book-key")
    assertThat(executions[3].bindings[3]).isEqualTo(9L)
  }

  @Test
  fun `abort writes no conflict clause, like the cached entity statements`() {
    val connection = newConnection()
    val context = operationContext(
      connection = connection,
      conflictAlgorithm = CONFLICT_ABORT
    )
    context.bindValues["a"] = "first"
    context.executeBoundInsert("books")
    context.executeBoundUpdate(
      tableName = "books",
      resolutionColumn = "id",
      resolutionValue = 1L
    )

    assertThat(connection.sqliteConnection.executions.map(RecordedExecution::sql))
      .containsExactly("INSERT INTO books(a) VALUES (?)", "UPDATE books SET a=? WHERE id=?")
      .inOrder()
  }

  @Test
  fun `native traversal aligns columns and bindings after collisions growth and repeated refill`() {
    val connection = newConnection()
    val context = operationContext(connection)
    val values = context.bindValues
    val collidingColumns = listOf("Aa", "BB").flatMap { first ->
      listOf("Aa", "BB").flatMap { second ->
        listOf("Aa", "BB").flatMap { third ->
          listOf("Aa", "BB").map { fourth -> "$first$second$third$fourth" }
        }
      }
    }
    repeat(3) { refill ->
      values.clear()
      collidingColumns.forEachIndexed { index, column -> values[column] = index.toLong() + refill }
      collidingColumns
        .take(4)
        .forEach(values::remove)
      values[collidingColumns.first()] = 99L
      val expected = collidingColumns.drop(4)
        .associateWith { collidingColumns.indexOf(it).toLong() + refill } + (collidingColumns.first() to 99L)
      context.executeBoundInsert("books")
      assertBoundColumns(
        execution = connection.sqliteConnection.executions.last(),
        expected = expected
      )
      context.executeBoundUpdate(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = 5L
      )
      val execution = connection.sqliteConnection.executions.last()
      assertBoundColumns(
        execution = execution,
        expected = expected
      )
      assertThat(execution.bindings[values.size + 1]).isEqualTo(5L)
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
      ),
      BindingCase(
        label = "true",
        value = true,
        expected = 1L
      ),
      BindingCase(
        label = "false",
        value = false,
        expected = 0L
      )
    ).forEach { case ->
      val connection = newConnection()
      val context = operationContext(connection)
      context.bindValues["value"] = case.value
      context.executeBoundUpdate(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = case.value
      )
      val execution = connection.sqliteConnection.executions.single()

      assertWithMessage(case.label)
        .that(execution.sql)
        .isEqualTo("UPDATE books SET value=? WHERE id=?")
      for (index in 1..2) {
        assertWithMessage("${case.label}: binding $index")
          .that(execution.bindings[index])
          .isEqualTo(case.expected)
      }
    }
  }

  private fun operationContext(
    connection: RecordingDbConnection,
    conflictAlgorithm: Int = CONFLICT_NONE
  ) = OperationContext(
    adapter = TestAdapter(),
    configuration = OperationConfigurationSnapshot(
      connection = connection.dbConnection,
      conflictAlgorithm = conflictAlgorithm
    )
  )
}

internal fun OperationContext.executeBoundInsert(tableName: String) =
  withBoundInsertStatement(tableName) { statement, results -> results.executeInsert(statement) }

internal fun OperationContext.executeBoundUpdate(
  tableName: String,
  resolutionColumn: String,
  resolutionValue: Any
) = withBoundUpdateStatement(
  tableName = tableName,
  resolutionColumn = resolutionColumn,
  resolutionValue = resolutionValue
) { statement, results ->
  results.executeUpdateDelete(statement)
}

private fun assertBoundColumns(
  execution: RecordedExecution,
  expected: Map<String, Any>
) {
  val columns = when {
    execution.sql.startsWith("INSERT") -> execution.sql
      .substringAfter('(')
      .substringBefore(')')
      .split(',')
    else -> execution.sql
      .substringAfter(" SET ")
      .substringBefore(" WHERE ")
      .split(',')
      .map { it.removeSuffix("=?") }
  }
  assertThat(columns).containsExactlyElementsIn(expected.keys)
  columns.forEachIndexed { index, column ->
    assertWithMessage("${execution.sql}: $column")
      .that(execution.bindings[index + 1])
      .isEqualTo(expected.getValue(column))
  }
}
