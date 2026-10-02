package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import org.junit.Test

internal class VariableArgsOperationHelperTest {
  @Test
  fun `reused SQL builder follows changing tables columns and operation kinds`() {
    val connection = newConnection()
    val manager = EntityDbManager(connection.connection)
    val helper = VariableArgsOperationHelper(CONFLICT_IGNORE)
    val values = SimpleArrayMap<String, Any>()
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

    assertThat(connection.statementSql)
      .containsExactly(
        "INSERT OR IGNORE INTO books(a,b) VALUES (?,?)",
        "INSERT OR IGNORE INTO authors(a) VALUES (?)",
        "UPDATE OR IGNORE books SET a=? WHERE key=?",
        "UPDATE OR IGNORE authors SET a=?,b=? WHERE id=?",
        "INSERT OR IGNORE INTO books(a,b) VALUES (?,?)"
      )
      .inOrder()
    assertThat(connection.statementBindings)
      .containsExactly(
        mapOf(1 to "first", 2 to "second"),
        mapOf(1 to "first"),
        mapOf(1 to "first", 2 to "book-key"),
        mapOf(1 to "first", 2 to 2L, 3 to 9L),
        mapOf(1 to "first", 2 to 2L)
      )
      .inOrder()
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
      val values = SimpleArrayMap<String, Any>()
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
