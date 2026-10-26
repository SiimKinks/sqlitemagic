package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteProgram
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.FLOAT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.NUMBER
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.identity
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.nullableReadType
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.appendNativeLiteral
import com.siimkinks.sqlitemagic.internal.sql.bindNativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SqlDescriptorTest {
  @Test
  fun `built-in values keep native storage`() {
    val values = listOf(
      7L.asValue().node to NativeValue.Integer(7L),
      3.asValue().node to NativeValue.Integer(3L),
      1.5.asValue().node to NativeValue.Real(1.5),
      "text".asValue().node to NativeValue.Text("text"),
      true.asValue().node to NativeValue.Integer(1L),
      null.asValue(SqlType.STRING.nullBypassParameterEncoding()).node to NativeValue.Null
    )

    values.forEach { (node, expected) ->
      assertThat(node).isEqualTo(ExpressionNode.Bound(expected))
    }
    val byte = 7.toByte()
      .asValue().node as ExpressionNode.Bound
    assertThat((byte.argument as NativeValue.Blob).value).isEqualTo(byteArrayOf(7))
  }

  @Test
  fun `owned blob is copied once at the host boundary`() {
    val bytes = byteArrayOf(1, 2)
    val expression = bytes.asValue()
    bytes[0] = 9
    val argument = (expression.node as ExpressionNode.Bound).argument as NativeValue.Blob
    val statement = mock<SupportSQLiteProgram>()
    val bound = argumentCaptor<ByteArray>()

    bindNativeValue(
      statement = statement,
      index = 1,
      value = argument
    )

    assertThat(argument.value).isEqualTo(byteArrayOf(1, 2))
    verify(statement).bindBlob(
      index = eq(1),
      value = bound.capture()
    )
    assertThat(bound.firstValue).isSameInstanceAs(argument.value)
  }

  @Test
  fun `direct sentinel and contextual null reads stay distinct`() {
    var decodes = 0
    val descriptor = nullableReadType(
      storage = SqlStorage.STRING,
      encode = String::toString,
      decode = { value ->
        decodes++
        value ?: "sentinel"
      },
      encodingNullness = Required
    )
    val cursor = mock<Cursor>()
    whenever(cursor.isNull(0)).thenReturn(true)

    val direct = "NULL".expr(descriptor)
      .selectRead(cursor, 0)
    val contextual = "NULL".expr(descriptor.nullable)
      .selectRead(cursor, 0)
    assertThat(direct).isEqualTo("sentinel")
    assertThat(contextual).isNull()
    assertThat(decodes).isEqualTo(1)
  }

  @Test
  fun `required SQL may have a nullable decoder result`() {
    val descriptor = requiredReadType(
      storage = SqlStorage.STRING,
      encode = ::identity,
      decode = fun(_: String): String? = null,
      encodingNullness = SqlNullable
    )
    val cursor = mock<Cursor>()
    whenever(cursor.getString(0)).thenReturn("present")

    val actual = "'present'".expr(descriptor)
      .selectRead(cursor, 0)
    assertThat(actual).isNull()
  }

  @Test
  fun `required raw reader rejects actual NULL while nullable reader bypasses it`() {
    val cursor = mock<Cursor>()
    whenever(cursor.isNull(0)).thenReturn(true)

    assertThat(
      sqlExceptionMessage {
        "NULL".expr(LONG)
          .selectRead(cursor, 0)
      }
    ).isEqualTo("Required SQL result returned SQL NULL")
    val actual = "NULL".expr(LONG.nullable)
      .selectRead(cursor, 0)
    assertThat(actual).isNull()
  }

  @Test
  fun `a transformer descriptor reads required SQL NULL like the built-in types`() {
    val descriptor = requiredReadType(
      storage = SqlStorage.STRING,
      encode = ::identity,
      decode = ::identity,
      encodingNullness = Required
    )
    val cursor = mock<Cursor>()
    whenever(cursor.isNull(0)).thenReturn(true)

    val message = sqlExceptionMessage {
      "NULL".expr(descriptor)
        .selectRead(cursor, 0)
    }

    assertThat(message).isEqualTo("Required SQL result returned SQL NULL")
  }

  @Test
  fun `SQL operand never enters a broad host encoder`() {
    var encodes = 0
    val descriptor: EncodingType<Any, Any, Required, Required> = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { value: Any ->
        encodes++
        value.toString()
      },
      decode = ::identity,
      encodingNullness = Required
    )

    val failure = assertThrows(IllegalArgumentException::class.java) {
      1L.asValue()
        .asValue(descriptor)
    }

    assertThat(failure).hasMessageThat().isEqualTo("SQL expressions and queries are not host values")
    assertThat(encodes).isEqualTo(0)
  }

  @Test
  fun `literal encoding escapes strings and keeps blob bytes`() {
    data class LiteralCase(
      val label: String,
      val value: NativeValue,
      val expected: String
    )
    listOf(
      LiteralCase(
        label = "text with a quote",
        value = NativeValue.Text("O'Brien"),
        expected = "'O''Brien'"
      ),
      LiteralCase(
        label = "blob",
        value = NativeValue.Blob.copyOf(byteArrayOf(0, 127, -1)),
        expected = "X'007FFF'"
      ),
      LiteralCase(
        label = "text with a NUL character",
        value = NativeValue.Text("a\u0000b"),
        expected = "CAST(X'610062' AS TEXT)"
      )
    ).forEach { case ->
      assertWithMessage(case.label)
        .that(
          buildString {
            appendNativeLiteral(
              sql = this,
              value = case.value
            )
          }
        )
        .isEqualTo(case.expected)
    }
  }

  @Test
  fun `NaN is rejected at every encoding boundary`() {
    val doubleParameter = 0.0.asValue()
      .asParameter("double")
    val floatParameter = 0f.asValue()
      .asParameter("float")
    val cases: List<Pair<String, () -> Unit>> = listOf(
      "Double value" to { Double.NaN.asValue() },
      "Float value" to { Float.NaN.asValue() },
      "Double literal" to { Double.NaN.asLiteral() },
      "Float literal" to { Float.NaN.asLiteral() },
      "Double raw argument" to { "?".expr(DOUBLE, Double.NaN) },
      "Float raw argument" to { "?".expr(FLOAT, Float.NaN) },
      "Double parameter assignment" to { doubleParameter TO Double.NaN },
      "Float parameter assignment" to { floatParameter TO Float.NaN }
    )

    cases.forEach { (label, operation) ->
      assertWithMessage(label)
        .that(assertThrows(IllegalArgumentException::class.java, operation))
        .hasMessageThat()
        .isEqualTo("NaN cannot be encoded as a required SQL REAL value")
    }
  }

  @Test
  fun `infinite real literals render as numerals SQLite reads as infinity`() {
    val query = SELECT.COLUMNS(Double.POSITIVE_INFINITY.asLiteral(), Double.NEGATIVE_INFINITY.asLiteral())

    assertThat(query.compile().sql).isEqualTo("SELECT 9e999, -9e999")
    BundledSqliteDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(listOf(Row2(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)))
    }
  }

  @Test
  fun `NUMBER reads INTEGER and REAL results and rejects stored text`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val numbers = SELECT
        .RAW("SELECT age, age / 2.0 FROM person WHERE id = 1")
        .readAs(NUMBER, NUMBER)
        .usingConnection(connection)
        .execute()
      val text = SELECT
        .RAW("SELECT name FROM person WHERE id = 1")
        .readAs(NUMBER)
        .usingConnection(connection)

      assertThat(numbers).isEqualTo(listOf(Row2(36L, 18.0)))
      assertThat(assertThrows(IllegalStateException::class.java, text::execute))
        .hasMessageThat()
        .isEqualTo("NUMBER requires an INTEGER or REAL SQL result")
    }
  }
}
