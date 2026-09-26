package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class TableSchemaSqlTest {
  @Test
  fun rootTableUsesItsSchemaWithAndWithoutAlias() {
    val cases = listOf(
      Triple(
        first = "main table",
        second = testTable(name = "books"),
        third = "SELECT 1 FROM main.books "
      ),
      Triple(
        first = "main alias",
        second = testTable<Any>(name = "books").`as`("book"),
        third = "SELECT 1 FROM main.books AS book "
      ),
      Triple(
        first = "temporary table",
        second = testTable(
          name = "books",
          temporary = true
        ),
        third = "SELECT 1 FROM temp.books "
      ),
      Triple(
        first = "temporary alias",
        second = testTable<Any>(
          name = "books",
          temporary = true
        ).`as`("book"),
        third = "SELECT 1 FROM temp.books AS book "
      )
    )

    for ((label, table, expectedSql) in cases) {
      val sql = (Select
        .column(Select.asColumn(1))
        .from(table)
        .compile() as CompiledSelectDetails).sql
      assertWithMessage(label)
        .that(sql)
        .isEqualTo(expectedSql)
    }
  }

  @Test
  fun equalRawNamesAndRepeatedSourcesKeepTheirOwnSchemaAndAliases() {
    val main = testTable<Any>(name = "books")
    val temporary = testTable<Any>(name = "books", temporary = true)

    val sql = (Select
      .column(Select.asColumn(1))
      .from(main.`as`("first_main"))
      .innerJoin(temporary.`as`("first_temp"))
      .innerJoin(main.`as`("other_main"))
      .innerJoin(temporary.`as`("other_temp"))
      .compile() as CompiledSelectDetails).sql

    assertThat(sql).isEqualTo(
      "SELECT 1 FROM main.books AS first_main " +
          "INNER JOIN temp.books AS first_temp " +
          "INNER JOIN main.books AS other_main " +
          "INNER JOIN temp.books AS other_temp "
    )
  }

  @Test
  fun anonymousSourceRemainsUnqualified() {
    val sql = (Select
      .column(Select.asColumn(1))
      .from(Table.ANONYMOUS_TABLE)
      .compile() as CompiledSelectDetails).sql

    assertThat(sql).isEqualTo("SELECT 1 FROM  ")
  }
}
