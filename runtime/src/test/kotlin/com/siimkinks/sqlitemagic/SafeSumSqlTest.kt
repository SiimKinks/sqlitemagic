package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class SafeSumSqlTest {
  @Test
  fun safeSumAndDistinctSumRenderTotal() {
    val cases = listOf(
      "sum" to Select.sum(Select.asColumn(7)),
      "sumDistinct" to Select.sumDistinct(Select.asColumn(7))
    )
    for ((label, column) in cases) {
      val sql = StringBuilder()
      column.appendSql(sql)
      val expected = when (label) {
        "sum" -> "total(7)"
        else -> "total(DISTINCT 7)"
      }
      assertWithMessage(label)
        .that(sql.toString())
        .isEqualTo(expected)
    }
  }
}
