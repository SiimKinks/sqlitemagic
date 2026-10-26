package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.nullableReadType
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SqlParameterContractTest {
  @Test
  fun `exact registered input type owns parameter binding`() {
    val parameter = Token("initial")
      .asValue(TOKEN_TYPE)
      .asParameter("token")
    val exact: SqlParameter<Token, Token, Required, Token> = parameter
    val assignment = exact TO Token("replacement")

    assertThat(assignment.argument).isEqualTo(NativeValue.Text("db:replacement"))
  }

  @Test
  fun `nullable host adapter bypasses required encoder for null`() {
    var encodes = 0
    val descriptor = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { token: Token ->
        encodes++
        token.value
      },
      decode = ::Token,
      encodingNullness = Required
    )

    val parameter = (null as Token?)
      .asValue(descriptor.nullBypassParameterEncoding())
      .asParameter("token")
    val exact: SqlParameter<Token?, Token, SqlNullable, Token?> = parameter
    val assignment = exact TO null

    assertThat(assignment.argument).isEqualTo(NativeValue.Null)
    assertThat(encodes).isEqualTo(0)
  }

  @Test
  fun `registered nullable host encoder receives null for every initial value`() {
    val encodedValues = mutableListOf<Token?>()
    val descriptor = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { token: Token? ->
        encodedValues += token
        token?.value ?: "encoded-null"
      },
      decode = ::Token,
      encodingNullness = Required
    )

    val expression = (null as Token?).asValue(descriptor)
    val fromNull: SqlParameter<Token?, Token, Required, Token> = expression.asParameter("token")
    val fromValue: SqlParameter<Token?, Token, Required, Token> = Token("initial")
      .asValue(descriptor)
      .asParameter("token")

    assertThat(expression.node).isEqualTo(ExpressionNode.Bound(NativeValue.Text("encoded-null")))
    assertThat(listOf(fromNull TO null, fromValue TO null).map(ParameterAssignment::argument)).isEqualTo(
      List(2) { NativeValue.Text("encoded-null") }
    )
    assertThat(encodedValues).containsExactly(null, Token("initial"), null, null)
      .inOrder()
  }

  @Test
  fun `encoder-produced null binds SQL NULL and uses contextual result null`() {
    var decodes = 0
    val descriptor = nullableReadType(
      storage = SqlStorage.STRING,
      encode = { _: Token -> null },
      decode = { value ->
        decodes++
        value ?: "sentinel"
      },
      encodingNullness = SqlNullable
    )
    val cursor = mock<Cursor>()
    whenever(cursor.isNull(0)).thenReturn(true)

    val expression = Token("initial").asValue(descriptor)
    val exact: SqlParameter<Token, String, SqlNullable, String?> = expression.asParameter("token")
    val actual = exact.selectRead(cursor, 0)

    assertThat(expression.node).isEqualTo(ExpressionNode.Bound(NativeValue.Null))
    assertThat(actual).isNull()
    assertThat(decodes).isEqualTo(0)
  }

  @Test
  fun `bindings reject missing, unexpected and duplicate parameters before connection resolution`() {
    val parameter = true.asValue()
      .asParameter("x")
    val impostor = true.asValue()
      .asParameter("x")
    val plan = SELECT.COLUMN(parameter)
      .WHERE(parameter)
      .compile()
    val cases = listOf(
      "missing" to { plan.bind() },
      "missing at execution" to plan::execute,
      "unexpected" to { plan.bind(impostor TO true) },
      "duplicate" to { plan.bind(parameter TO true, parameter TO false) }
    )

    assertThat(
      cases.map { (label, binding) ->
        label to assertThrows(IllegalArgumentException::class.java) { binding() }.message
      }
    ).isEqualTo(
      listOf(
        "missing" to "Missing parameter 'x'",
        "missing at execution" to "Missing parameter 'x'",
        "unexpected" to "Unexpected parameter 'x'",
        "duplicate" to "Duplicate parameter 'x'"
      )
    )
  }

  @Test
  fun `nullable parameter binds SQL NULL or its value per binding`() {
    val parameter = (null as Long?).asValue()
      .asParameter("optional")
    val plan = SELECT.COLUMN(parameter)
      .compile()

    assertThat(
      listOf(
        plan.bind(parameter TO null),
        plan.bind(parameter TO 2L)
      ).map(SelectPlan<*>::boundArguments)
    ).isEqualTo(
      listOf(
        listOf(NativeValue.Null),
        listOf(NativeValue.Integer(2L))
      )
    )
  }
}
