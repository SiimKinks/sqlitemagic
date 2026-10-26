package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.transformer.mockTransformerElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import org.junit.jupiter.api.Test

internal class ViewElementTest {
  @Test
  fun `view derives leaves scalar properties and names`() {
    val direct = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("direct")),
      selectionKey = "direct",
      generatedFieldName = "DIRECT"
    )
    val embeddedLeaf = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("embedded", "leaf")),
      selectionKey = "embedded_leaf",
      generatedFieldName = "EMBEDDED_LEAF"
    )
    val embedded = mockViewEmbeddedPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("embedded")),
      properties = listOf(embeddedLeaf)
    )
    val transformed = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("transformed")),
      selectionKey = "transformed",
      generatedFieldName = "TRANSFORMED",
      transformer = mockTransformerElement()
    )
    val tableProjection = mockViewProjectionPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("table")),
      target = GenerationNames("Table")
    )
    val viewProjection = mockViewProjectionPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("view")),
      target = GenerationNames("View")
    )
    val view = mockViewElement(
      parsedType = mockParsedType(
        typeName = ClassName(PACKAGE, "Nested", "ReportView")
      ),
      identity = SqliteSchemaIdentity(
        schema = MAIN,
        identifier = SqliteIdentifier("active_reports")
      ),
      properties = listOf(direct, embedded, transformed, tableProjection, viewProjection)
    )

    assertThat(view.leaves)
      .containsExactly(direct, embeddedLeaf, transformed, tableProjection, viewProjection)
      .inOrder()
    assertThat(view.scalarProperties)
      .containsExactly(direct, embeddedLeaf, transformed)
      .inOrder()
    assertThat(view.modelName)
      .isEqualTo("ReportView")
    assertThat(view.viewName)
      .isEqualTo("active_reports")
  }
}
