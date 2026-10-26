package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import org.junit.Test

internal class QueryDependenciesTest {
  @Test
  fun `SQLite schema identity preserves raw spelling and uses ASCII case folding for keys`() {
    val main = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "Books"
    )
    val otherCase = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "bOOKS"
    )
    assertThat(otherCase).isNotEqualTo(main)
    assertThat(otherCase.normalizedKey).isEqualTo(main.normalizedKey)
    val temporary = SqliteSchemaIdentity.from(
      schema = TEMPORARY,
      rawName = "BOOKS"
    )
    assertThat(main.normalizedKey)
      .isEqualTo(
        SqliteSchemaKey(
          schema = MAIN,
          normalizedName = "books"
        )
      )
    assertThat(main.normalizedKey)
      .isNotEqualTo(temporary.normalizedKey)

    val accentedUpper = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "Ärger"
    )
    val accentedLower = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "ärger"
    )
    assertThat(accentedUpper.normalizedKey)
      .isEqualTo(
        SqliteSchemaKey(
          schema = MAIN,
          normalizedName = "Ärger"
        )
      )
    assertThat(accentedUpper.normalizedKey)
      .isNotEqualTo(accentedLower.normalizedKey)
  }

  @Test
  fun `builder deduplicates direct sources by identity in first-seen order`() {
    val books = SqliteQuerySource(
      identity = SqliteSchemaIdentity.from(
        schema = MAIN,
        rawName = "Books"
      ),
      kind = TABLE
    )
    val reports = SqliteQuerySource(
      identity = SqliteSchemaIdentity.from(
        schema = TEMPORARY,
        rawName = "reports"
      ),
      kind = VIEW
    )
    val builder = QueryDependencies.Builder()
    listOf(
      books,
      SqliteQuerySource(
        identity = SqliteSchemaIdentity.from(
          schema = MAIN,
          rawName = "books"
        ),
        kind = TABLE
      ),
      reports,
      books,
      SqliteQuerySource(
        identity = SqliteSchemaIdentity.from(
          schema = TEMPORARY,
          rawName = "REPORTS"
        ),
        kind = VIEW
      )
    ).forEach(builder::addSource)

    val dependencies = builder.build()
    assertThat(dependencies.directSources)
      .containsExactly(books, reports)
      .inOrder()
  }
}
