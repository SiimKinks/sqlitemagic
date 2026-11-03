package com.siimkinks.sqlitemagic.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class SqlIdentifierQuotingTest {
  @Test
  fun `appended identifiers are quoted with embedded quotes doubled`() {
    val cases = mapOf(
      "plain" to ("books" to """"books""""),
      "keyword" to ("order" to """"order""""),
      "embedded quote" to ("""my"table""" to """"my""table"""")
    )

    assertThat(cases.mapValues { (_, case) -> buildString { appendQuotedSqlIdentifier(case.first) } })
      .isEqualTo(cases.mapValues { (_, case) -> case.second })
  }
}
