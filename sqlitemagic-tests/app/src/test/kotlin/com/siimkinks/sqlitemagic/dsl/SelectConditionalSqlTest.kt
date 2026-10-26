package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.COALESCE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SelectConditionalSqlTest {
  @Test
  fun `generated value descriptor composes only with its transformed column`() {
    SELECT
      .COLUMN(COALESCE(ACCOUNT.ID, AccountId("guest").asValue()))
      .FROM(ACCOUNT)
      .assertSql("""SELECT COALESCE("account"."id", ?) FROM "main"."account"""")
    assertThat(assertThrows<IllegalArgumentException> { COALESCE(ACCOUNT.ID, ACCOUNT.LABEL) })
      .hasMessageThat()
      .isEqualTo("COALESCE values must decode alike: one type with one transformer, or native numbers read as Number")
  }
}
