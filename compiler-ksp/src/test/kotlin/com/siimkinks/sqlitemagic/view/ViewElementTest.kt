package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.index.SqliteIdentifier
import com.siimkinks.sqlitemagic.index.SqliteSchema
import com.siimkinks.sqlitemagic.index.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.mockModelConstruction
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.transformer.mockTransformerElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.STRING
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ViewElementTest {
  @Test
  fun `view retains the complete durable property tree`() {
    val titlePath = mockPropertyPath("title")
    val detailsPath = mockPropertyPath("details")
    val postalCodePath = mockPropertyPath("details", "postalCode")
    val amountPath = mockPropertyPath("amount")
    val authorPath = mockPropertyPath("author")
    val summaryPath = mockPropertyPath("summary")
    val viewType = mockParsedType(
      typeName = ClassName(PACKAGE, "ReportView")
    )
    val detailsType = mockParsedType(
      typeName = ClassName(PACKAGE, "Details")
    )
    val authorType = mockParsedType(
      typeName = ClassName(PACKAGE, "Author")
    )
    val summaryType = mockParsedType(
      typeName = ClassName(PACKAGE, "AuthorSummary")
    )
    val transformer = mockTransformerElement()
    val title = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = titlePath),
      deserializedType = mockParsedType(typeName = STRING),
      selectionKey = "title",
      generatedFieldName = "TITLE"
    )
    val postalCode = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = postalCodePath),
      deserializedType = mockParsedType(typeName = STRING),
      isNullable = true,
      selectionKey = "details_postal_code",
      generatedFieldName = "DETAILS_POSTAL_CODE"
    )
    val details = mockViewEmbeddedPropertyElement(
      access = mockPropertyAccess(path = detailsPath),
      deserializedType = detailsType,
      isNullable = true,
      prefix = "details_",
      cumulativePrefix = "details_",
      construction = mockModelConstruction(
        constructorParameters = listOf(postalCodePath)
      ),
      properties = listOf(postalCode)
    )
    val amount = mockViewScalarPropertyElement(
      access = mockPropertyAccess(path = amountPath),
      deserializedType = transformer.deserializedType,
      selectionKey = "amount",
      generatedFieldName = "AMOUNT",
      transformer = transformer
    )
    val author = mockViewProjectionPropertyElement(
      access = mockPropertyAccess(path = authorPath),
      deserializedType = authorType,
      selectionKey = "author",
      targetKind = ViewProjectionKind.TABLE,
      targetArtifactStem = "Author",
      targetConstruction = mockModelConstruction(
        constructorParameters = listOf(
          mockPropertyPath("id"),
          mockPropertyPath("name")
        )
      ),
      expandedWidth = 2
    )
    val summary = mockViewProjectionPropertyElement(
      access = mockPropertyAccess(path = summaryPath),
      deserializedType = summaryType,
      isNullable = true,
      selectionKey = "summary",
      targetKind = ViewProjectionKind.VIEW,
      targetArtifactStem = "AuthorSummary",
      targetConstruction = mockModelConstruction(
        constructorParameters = listOf(
          mockPropertyPath("name"),
          mockPropertyPath("count")
        )
      ),
      expandedWidth = 3
    )
    val identity = SqliteSchemaIdentity(
      schema = SqliteSchema.TEMPORARY,
      identifier = SqliteIdentifier.from("active_report")
    )
    val query = mockViewQueryElement(
      ownerType = viewType.typeName as ClassName,
      propertyName = "DEFINITION",
      declaredType = ClassName(PACKAGE, "CompiledSelect")
    )
    val construction = mockModelConstruction(
      constructorParameters = listOf(
        titlePath,
        detailsPath,
        amountPath,
        authorPath,
        summaryPath
      )
    )
    val actual = mockViewElement(
      parsedType = viewType,
      identity = identity,
      artifactStem = "Container_Report",
      declarationOrder = 4,
      moduleName = "Reports",
      construction = construction,
      query = query,
      properties = listOf(title, details, amount, author, summary),
      isPublic = false
    )

    assertThat(actual)
      .isEqualTo(
        ViewElement(
          parsedType = viewType,
          identity = identity,
          artifactStem = "Container_Report",
          declarationOrder = 4,
          moduleName = "Reports",
          construction = construction,
          query = query,
          properties = listOf(
            ViewScalarPropertyElement(
              access = mockPropertyAccess(path = titlePath),
              deserializedType = mockParsedType(typeName = STRING),
              isNullable = false,
              selectionKey = "title",
              generatedFieldName = "TITLE",
              transformer = null
            ),
            ViewEmbeddedPropertyElement(
              access = mockPropertyAccess(path = detailsPath),
              deserializedType = detailsType,
              isNullable = true,
              prefix = "details_",
              cumulativePrefix = "details_",
              construction = mockModelConstruction(
                constructorParameters = listOf(postalCodePath)
              ),
              properties = listOf(
                ViewScalarPropertyElement(
                  access = mockPropertyAccess(path = postalCodePath),
                  deserializedType = mockParsedType(typeName = STRING),
                  isNullable = true,
                  selectionKey = "details_postal_code",
                  generatedFieldName = "DETAILS_POSTAL_CODE",
                  transformer = null
                )
              )
            ),
            ViewScalarPropertyElement(
              access = mockPropertyAccess(path = amountPath),
              deserializedType = transformer.deserializedType,
              isNullable = false,
              selectionKey = "amount",
              generatedFieldName = "AMOUNT",
              transformer = transformer
            ),
            ViewProjectionPropertyElement(
              access = mockPropertyAccess(path = authorPath),
              deserializedType = authorType,
              isNullable = false,
              selectionKey = "author",
              targetKind = ViewProjectionKind.TABLE,
              targetArtifactStem = "Author",
              targetConstruction = mockModelConstruction(
                constructorParameters = listOf(
                  mockPropertyPath("id"),
                  mockPropertyPath("name")
                )
              ),
              expandedWidth = 2
            ),
            ViewProjectionPropertyElement(
              access = mockPropertyAccess(path = summaryPath),
              deserializedType = summaryType,
              isNullable = true,
              selectionKey = "summary",
              targetKind = ViewProjectionKind.VIEW,
              targetArtifactStem = "AuthorSummary",
              targetConstruction = mockModelConstruction(
                constructorParameters = listOf(
                  mockPropertyPath("name"),
                  mockPropertyPath("count")
                )
              ),
              expandedWidth = 3
            )
          ),
          isPublic = false
        )
      )
  }

  @Test
  fun `view derives leaves scalar properties width names and schema`() {
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
      targetKind = ViewProjectionKind.TABLE,
      targetArtifactStem = "TableTarget",
      expandedWidth = 2
    )
    val viewProjection = mockViewProjectionPropertyElement(
      access = mockPropertyAccess(path = mockPropertyPath("view")),
      targetKind = ViewProjectionKind.VIEW,
      targetArtifactStem = "ViewTarget",
      expandedWidth = 3
    )
    val view = mockViewElement(
      parsedType = mockParsedType(
        typeName = ClassName(PACKAGE, "Nested", "ReportView")
      ),
      identity = SqliteSchemaIdentity(
        schema = SqliteSchema.MAIN,
        identifier = SqliteIdentifier.from("active_reports")
      ),
      artifactStem = "Nested_ReportView",
      properties = listOf(direct, embedded, transformed, tableProjection, viewProjection)
    )

    assertThat(view.leaves)
      .containsExactly(direct, embeddedLeaf, transformed, tableProjection, viewProjection)
      .inOrder()
    assertThat(view.scalarProperties)
      .containsExactly(direct, embeddedLeaf, transformed)
      .inOrder()
    assertThat(view.expandedWidth)
      .isEqualTo(8)
    assertThat(view.modelName)
      .isEqualTo("ReportView")
    assertThat(view.packageName)
      .isEqualTo(PACKAGE)
    assertThat(view.viewName)
      .isEqualTo("active_reports")
    assertThat(view.schema)
      .isEqualTo(SqliteSchema.MAIN)
    assertThat(view.generationNames)
      .isEqualTo(mockViewGenerationNames(
        packageName = PACKAGE,
        artifactStem = "Nested_ReportView"
      ))
    assertThat(view.generationNames.daoClassName)
      .isEqualTo(ClassName(PACKAGE, "SqliteMagic_Nested_ReportView_Dao"))
    assertThat(view.generationNames.tableClassName)
      .isEqualTo(ClassName(PACKAGE_ROOT, "Nested_ReportViewTable"))
    assertThat(transformed.serializedType)
      .isEqualTo(transformed.transformer?.serializedType)
  }

  @Test
  fun `projection width must be positive`() {
    assertThrows<IllegalArgumentException> {
      mockViewProjectionPropertyElement(expandedWidth = 0)
    }
  }
}
