package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.squareup.kotlinpoet.BYTE_ARRAY
import org.junit.jupiter.api.Test

internal class ModelElementTest {
  @Test
  fun `table identity projections preserve optional IDs and eligible unique keys`() {
    val idColumn = mockColumnElement(id = mockIdElement())
    val eligibleUniqueColumn = mockColumnElement(
      columnName = "email",
      isUnique = true
    )
    val nullableUniqueColumn = mockColumnElement(
      columnName = "nickname",
      isNullable = true,
      isUnique = true
    )
    val blobUniqueColumn = mockColumnElement(
      columnName = "binary_key",
      deserializedType = mockParsedType(
        typeName = BYTE_ARRAY,
        sqlStorageType = SqlStorageType.BYTE_ARRAY
      ),
      isUnique = true
    )
    val table = mockTableElement(
      properties = listOf(idColumn, eligibleUniqueColumn, nullableUniqueColumn, blobUniqueColumn)
        .map(::mockColumnPropertyElement)
    )
    val tableWithoutId = mockTableElement(
      properties = listOf(eligibleUniqueColumn, nullableUniqueColumn, blobUniqueColumn)
        .map(::mockColumnPropertyElement)
    )

    assertThat(table.idColumn)
      .isEqualTo(idColumn)
    assertThat(table.identityColumns)
      .containsExactly(idColumn, eligibleUniqueColumn)
    assertThat(tableWithoutId.idColumn)
      .isNull()
    assertThat(tableWithoutId.identityColumns)
      .containsExactly(eligibleUniqueColumn)
  }
}
