package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import org.junit.Test

internal class VariableArgsOperationHelperTest {
  @Test
  fun `reused SQL builder follows changing tables columns and operation kinds`() {
    val connection = newConnection()
    val manager = EntityDbManager(connection.connection)
    val helper = VariableArgsOperationHelper(CONFLICT_IGNORE)
    val values = MutableScatterMap<String, Any>()
      .apply {
        put("b", "second")
        put("a", "first")
      }
    helper.compileInsertStatement(
      tableName = "books",
      maxColumns = 3,
      values = values,
      manager = manager
    )
    values.remove("b")
    helper.compileInsertStatement(
      tableName = "authors",
      maxColumns = 3,
      values = values,
      manager = manager
    )
    helper.compileUpdateStatement(
      tableName = "books",
      maxColumns = 3,
      values = values,
      resolutionColumn = "key",
      resolutionValue = "book-key",
      manager = manager
    )
    values.put("b", 2)
    helper.compileUpdateStatement(
      tableName = "authors",
      maxColumns = 3,
      values = values,
      resolutionColumn = "id",
      resolutionValue = 9L,
      manager = manager
    )
    helper.compileInsertStatement(
      tableName = "books",
      maxColumns = 3,
      values = values,
      manager = manager
    )

    val expectedValues = listOf(
      mapOf("a" to "first", "b" to "second"),
      mapOf("a" to "first"),
      mapOf("a" to "first"),
      mapOf("a" to "first", "b" to 2L),
      mapOf("a" to "first", "b" to 2L)
    )
    val expectedPrefixes = listOf(
      "INSERT OR IGNORE INTO books",
      "INSERT OR IGNORE INTO authors",
      "UPDATE OR IGNORE books",
      "UPDATE OR IGNORE authors",
      "INSERT OR IGNORE INTO books"
    )
    assertThat(connection.recordingDatabase.compiledStatements).hasSize(5)
    connection.recordingDatabase.compiledStatements.forEachIndexed { index, statement ->
      assertThat(statement.sql).startsWith(expectedPrefixes[index])
      when (index) {
        2 -> assertThat(statement.sql).endsWith(" WHERE key=?")
        3 -> assertThat(statement.sql).endsWith(" WHERE id=?")
        else -> assertThat(statement.sql).endsWith(
          expectedValues[index].keys.joinToString(
            separator = ",",
            prefix = ") VALUES (",
            postfix = ")"
          ) { "?" }
        )
      }
      assertBoundColumns(
        statement = statement,
        expected = expectedValues[index]
      )
    }
    assertThat(connection.statementBindings[2][2]).isEqualTo("book-key")
    assertThat(connection.statementBindings[3][3]).isEqualTo(9L)
  }

  @Test
  fun `native traversal aligns columns and bindings after collisions growth and repeated refill`() {
    val connection = newConnection()
    val manager = EntityDbManager(connection.connection)
    val helper = VariableArgsOperationHelper(CONFLICT_NONE)
    val values = MutableScatterMap<String, Any>(initialCapacity = 0)
    // Each Aa/BB pair has the same String hash, yielding sixteen colliding keys.
    val columns = listOf("Aa", "BB").flatMap { first ->
      listOf("Aa", "BB").flatMap { second ->
        listOf("Aa", "BB").flatMap { third ->
          listOf("Aa", "BB").map { fourth -> first + second + third + fourth }
        }
      }
    }
    repeat(3) { refill ->
      values.clear()
      columns.forEachIndexed { index, column -> values[column] = index.toLong() + refill }
      columns.take(4).forEach(values::remove)
      values[columns.first()] = 99L
      val expected = columns.drop(4)
        .associateWith { columns.indexOf(it).toLong() + refill } + (columns.first() to 99L)
      helper.compileInsertStatement(
        tableName = "books",
        maxColumns = columns.size,
        values = values,
        manager = manager
      )
      assertBoundColumns(
        statement = connection.recordingDatabase.compiledStatements.last(),
        expected = expected
      )
      helper.compileUpdateStatement(
        tableName = "books",
        maxColumns = columns.size,
        values = values,
        resolutionColumn = "id",
        resolutionValue = 5L,
        manager = manager
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

    val nonByteArray = arrayOf("text")
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
        label = "primitive blob",
        value = byteArrayOf(1, 2),
        expected = byteArrayOf(1, 2)
      ),
      BindingCase(
        label = "boxed blob",
        value = arrayOf<Byte>(3, 4),
        expected = byteArrayOf(3, 4)
      ),
      BindingCase(
        label = "object string fallback",
        value = StringBuilder("text"),
        expected = "text"
      ),
      BindingCase(
        label = "non-byte array string fallback",
        value = nonByteArray,
        expected = nonByteArray.toString()
      )
    ).forEach { case ->
      val connection = newConnection()
      val values = MutableScatterMap<String, Any>()
      values.put("value", case.value)
      VariableArgsOperationHelper(CONFLICT_NONE)
        .compileUpdateStatement(
          tableName = "books",
          maxColumns = 1,
          values = values,
          resolutionColumn = "id",
          resolutionValue = case.value,
          manager = EntityDbManager(connection.connection)
        )
      val statement = connection.recordingDatabase.compiledStatements.single()

      assertWithMessage(case.label)
        .that(statement.sql)
        .isEqualTo("UPDATE books SET value=? WHERE id=?")
      for (index in 1..2) {
        when (val expected = case.expected) {
          is ByteArray -> assertWithMessage("${case.label}: binding $index")
            .that(statement.bindings[index] as ByteArray)
            .isEqualTo(expected)
          else -> assertWithMessage("${case.label}: binding $index")
            .that(statement.bindings[index])
            .isEqualTo(expected)
        }
      }
    }
  }
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
