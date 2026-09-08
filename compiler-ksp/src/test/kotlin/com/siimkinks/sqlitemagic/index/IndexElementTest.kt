package com.siimkinks.sqlitemagic.index

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.ClassName
import org.junit.jupiter.api.Test

internal class IndexElementTest {
  @Test
  fun `field index retains its complete durable generation boundary`() {
    val propertyPath = mockPropertyPath("details", "account")
    val identity = SqliteSchemaIdentity(
      schema = SqliteSchema.TEMPORARY,
      identifier = SqliteIdentifier.from("INDEX_Äccount")
    )
    val tableType = ClassName(PACKAGE, "Container", "Account")
    val column = IndexColumnElement(
      propertyPath = propertyPath,
      columnName = "details_account_id"
    )
    val actual = mockIndexElement(
      identity = identity,
      tableType = tableType,
      tableName = "container_account",
      kind = IndexKind.FIELD,
      columns = listOf(column),
      isUnique = true,
      sourcePropertyPath = propertyPath
    )

    assertThat(actual)
      .isEqualTo(
        IndexElement(
          identity = identity,
          tableType = tableType,
          tableName = "container_account",
          kind = IndexKind.FIELD,
          columns = listOf(column),
          isUnique = true,
          sourcePropertyPath = propertyPath
        )
      )
    assertThat(actual.name).isEqualTo("INDEX_Äccount")
    assertThat(actual.normalizedName).isEqualTo("index_Äccount")
    assertThat(actual.schema).isEqualTo(SqliteSchema.TEMPORARY)
    assertThat(actual.tableTypeKey).isEqualTo("$PACKAGE.Container.Account")
  }

  @Test
  fun `composite index retains ordered physical columns without a field origin`() {
    val firstColumn = mockIndexColumnElement(
      propertyPath = mockPropertyPath("details", "latitude"),
      columnName = "details_latitude"
    )
    val secondColumn = mockIndexColumnElement(
      propertyPath = mockPropertyPath("name"),
      columnName = "name"
    )
    val identity = mockSqliteSchemaIdentity(
      identifier = mockSqliteIdentifier(rawName = "project_location")
    )
    val tableType = ClassName(PACKAGE, "Project")
    val actual = mockIndexElement(
      identity = identity,
      tableType = tableType,
      tableName = "project",
      kind = IndexKind.COMPOSITE,
      columns = listOf(firstColumn, secondColumn),
      isUnique = false,
      sourcePropertyPath = null
    )

    assertThat(actual)
      .isEqualTo(
        IndexElement(
          identity = identity,
          tableType = tableType,
          tableName = "project",
          kind = IndexKind.COMPOSITE,
          columns = listOf(firstColumn, secondColumn),
          isUnique = false,
          sourcePropertyPath = null
        )
      )
    assertThat(actual.columns).containsExactly(firstColumn, secondColumn).inOrder()
  }

  @Test
  fun `round wrapper keeps originating files outside the durable index`() {
    val index = mockIndexElement()
    val originatingFiles = OriginatingFiles(
      files = emptySet(),
      isComplete = false
    )
    val actual = mockIndexRoundElement(
      index = index,
      originatingFiles = originatingFiles
    )

    assertThat(actual)
      .isEqualTo(
        IndexRoundElement(
          index = index,
          originatingFiles = originatingFiles
        )
      )
  }
}
