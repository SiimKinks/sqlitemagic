package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class TypedRowSelectTest {
  @Test
  fun `ordinal readers use consecutive offsets and retain independent null policies`() {
    val reads = mutableListOf<Int>()
    val sentinel = SqlExpression<String, SqlNullable, String>(
      node = ExpressionNode.Literal(NativeValue.Null),
      nonNullRead = STRING.nonNullRead,
      selectRead = { _, index ->
        reads += index
        "sentinel"
      },
      nullness = SqlNullable
    )
    val nullableDecoder = requiredReadType(
      storage = SqlStorage.LONG,
      encode = { value: Long? -> value ?: 0L },
      decode = { _: Long -> null as Long? },
      encodingNullness = Required
    )
    val query = SELECT
      .COLUMNS(sentinel, "7".expr(nullableDecoder))
    val rows = mock<Cursor>()
    whenever(rows.getLong(6)).thenReturn(7L)
    val read = query.node.selection
      .createExecutionReader(SqlRenderingContext())
      .open()

    assertThat(read(rows, 5)).isEqualTo(Row2("sentinel", null))
    assertThat(reads).containsExactly(5)
    verify(rows).getLong(6)
  }
}
