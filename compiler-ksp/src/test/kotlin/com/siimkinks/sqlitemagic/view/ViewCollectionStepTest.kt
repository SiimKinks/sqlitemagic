package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.CLASS_MEMBER
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.transformer.TransformerMethodElementImpl
import com.siimkinks.sqlitemagic.transformer.TransformerTypeElementImpl
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewCollectionStepTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `collects a complete durable view value`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CollectedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class Email(val value: String)

            object EmailTransformer {
              @ObjectToDbValue
              fun toDb(value: Email): String = value.value

              @DbValueToObject
              fun fromDb(value: String): Email = Email(value)
            }

            @Table("authors")
            data class Author(
              @Id val id: Long,
              val name: String
            )

            data class Details(
              @ViewColumn("postal_alias") val postalCode: String,
              val cityName: String
            ) {
              @IgnoreColumn
              val ignored: String = "ignored"
            }

            @View("inner_summary")
            data class InnerSummary(
              @ViewColumn("label") val label: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<Author, Select.SelectN> = error("compile-only")
              }
            }

            @View("author_summary")
            data class AuthorSummary(
              @ViewColumn("title") val title: String,
              @ViewColumn("email") val email: Email?,
              @Embedded(prefix = "details_") val details: Details,
              @ViewColumn("author") val author: Author,
              @ViewColumn("summary") val summary: InnerSummary?,
              val ignored: String = "ignored"
            ) {
              companion object {
                @ViewQuery
                val DEFINITION: CompiledSelect<Author, Select.SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()

    val emailType = ClassName(PACKAGE, "Email")
    val authorType = ClassName(PACKAGE, "Author")
    val detailsType = ClassName(PACKAGE, "Details")
    val innerSummaryType = ClassName(PACKAGE, "InnerSummary")
    val authorSummaryType = ClassName(PACKAGE, "AuthorSummary")
    val transformer = TransformerElement(
      deserializedType = TransformerTypeElementImpl(
        parsedType = mockParsedType(typeName = emailType),
        transformerName = "Email"
      ),
      serializedType = TransformerTypeElementImpl(
        parsedType = mockParsedType(typeName = STRING),
        transformerName = "String"
      ),
      objectToDbValueMethod = TransformerMethodElementImpl(
        methodName = "toDb",
        packageName = PACKAGE,
        ownerClassName = ClassName(PACKAGE, "EmailTransformer"),
        callableKind = CLASS_MEMBER
      ),
      dbValueToObjectMethod = TransformerMethodElementImpl(
        methodName = "fromDb",
        packageName = PACKAGE,
        ownerClassName = ClassName(PACKAGE, "EmailTransformer"),
        callableKind = CLASS_MEMBER
      ),
      serializedTypeCanBeNull = false
    )
    val authorConstruction = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(
        mockPropertyPath("id"),
        mockPropertyPath("name")
      ),
      defaultableParameters = emptySet()
    )
    val detailsConstruction = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(
        mockPropertyPath("details", "postalCode"),
        mockPropertyPath("details", "cityName")
      ),
      defaultableParameters = emptySet()
    )
    val innerSummaryConstruction = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(mockPropertyPath("label")),
      defaultableParameters = emptySet()
    )
    val expected = mockViewElement(
      parsedType = mockParsedType(typeName = authorSummaryType),
      identity = SqliteSchemaIdentity(
        schema = SqliteSchema.MAIN,
        identifier = SqliteIdentifier.from("author_summary")
      ),
      artifactStem = "AuthorSummary",
      declarationOrder = 1,
      moduleName = null,
      construction = ModelConstruction(
        strategy = PRIMARY_CONSTRUCTOR,
        constructorParameters = listOf(
          mockPropertyPath("title"),
          mockPropertyPath("email"),
          mockPropertyPath("details"),
          mockPropertyPath("author"),
          mockPropertyPath("summary"),
          mockPropertyPath("ignored")
        ),
        defaultableParameters = setOf(mockPropertyPath("ignored"))
      ),
      query = mockViewQueryElement(
        ownerType = authorSummaryType,
        propertyName = "DEFINITION",
        declaredType = ClassName("com.siimkinks.sqlitemagic", "CompiledSelect")
          .parameterizedBy(
            authorType,
            ClassName("com.siimkinks.sqlitemagic", "Select", "SelectN")
          )
      ),
      properties = listOf(
        mockViewScalarPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("title")),
          deserializedType = mockParsedType(typeName = STRING),
          isNullable = false,
          selectionKey = "title",
          generatedFieldName = "TITLE",
          transformer = null
        ),
        mockViewScalarPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("email")),
          deserializedType = mockParsedType(typeName = emailType.copy(nullable = true)),
          isNullable = true,
          selectionKey = "email",
          generatedFieldName = "EMAIL",
          transformer = transformer
        ),
        mockViewEmbeddedPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("details")),
          deserializedType = mockParsedType(typeName = detailsType),
          isNullable = false,
          prefix = "details_",
          cumulativePrefix = "details_",
          construction = detailsConstruction,
          properties = listOf(
            mockViewScalarPropertyElement(
              access = mockPropertyAccess(path = mockPropertyPath("details", "postalCode")),
              deserializedType = mockParsedType(typeName = STRING),
              isNullable = false,
              selectionKey = "details_postal_alias",
              generatedFieldName = "DETAILS_POSTAL_ALIAS",
              transformer = null
            ),
            mockViewScalarPropertyElement(
              access = mockPropertyAccess(path = mockPropertyPath("details", "cityName")),
              deserializedType = mockParsedType(typeName = STRING),
              isNullable = false,
              selectionKey = "details_city_name",
              generatedFieldName = "DETAILS_CITY_NAME",
              transformer = null
            )
          )
        ),
        mockViewProjectionPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("author")),
          deserializedType = mockParsedType(typeName = authorType),
          isNullable = false,
          selectionKey = "author",
          targetKind = ViewProjectionKind.TABLE,
          targetArtifactStem = "Author",
          targetConstruction = authorConstruction,
          expandedWidth = 2
        ),
        mockViewProjectionPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("summary")),
          deserializedType = mockParsedType(typeName = innerSummaryType.copy(nullable = true)),
          isNullable = true,
          selectionKey = "summary",
          targetKind = ViewProjectionKind.VIEW,
          targetArtifactStem = "InnerSummary",
          targetConstruction = innerSummaryConstruction,
          expandedWidth = 1
        )
      ),
      isPublic = true
    )

    assertThat(compilation.environment.viewElements.values.map(ViewElement::modelName))
      .containsExactly("InnerSummary", "AuthorSummary")
      .inOrder()
    assertThat(compilation.environment.viewElements.getValue(authorSummaryType.canonicalName))
      .isEqualTo(expected)
  }

  @Test
  fun `retries a generated dependency and keeps one durable view without live KSP symbols`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DeferredCollectedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery
            import com.siimkinks.sqlitemagic.LateCollectedRowTable

            private fun inferredQuery(): CompiledSelect<String, Select.Select1> {
              LateCollectedRowTable.LATE_COLLECTED_ROW
              return error("compile-only inferred query")
            }

            @View("deferred_collected_view")
            data class DeferredCollectedView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY = inferredQuery()
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          val steps = viewProcessingSteps(environment)
          steps.dropLast(1) + GeneratedSourceStep(environment) + steps.last()
        }
      )
      .isOk()

    val environment = compilation.environment
    assertThat(environment.processingRounds)
      .isAtLeast(2)
    assertThat(environment.viewElements.values.map(ViewElement::modelName))
      .containsExactly("DeferredCollectedView")
    assertThat(environment.viewElements)
      .hasSize(1)
    assertThat(environment.viewRoundElementsForCurrentRound)
      .isEmpty()
  }
}

private class GeneratedSourceStep(
  private val environment: Environment
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver): ProcessingStepResult {
    if (generated) return Continue
    generated = true
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = false),
        packageName = PACKAGE,
        fileName = "LateCollectedRow"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
          package $PACKAGE

          import com.siimkinks.sqlitemagic.annotation.Table

          @Table("late_collected_rows")
          data class LateCollectedRow(
            val value: String
          )
          """.trimIndent()
        )
      }
    return Continue
  }
}
