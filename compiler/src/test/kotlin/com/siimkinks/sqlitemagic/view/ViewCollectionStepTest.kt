package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.CLASS_MEMBER
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.transformer.TransformerMethodElementImpl
import com.siimkinks.sqlitemagic.transformer.TransformerTypeElementImpl
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.OriginRecordingStep
import com.siimkinks.sqlitemagic.utils.OriginatingFilesSnapshot
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.insertAfter
import com.siimkinks.sqlitemagic.utils.insertBeforeLast
import com.siimkinks.sqlitemagic.utils.isOk
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.STRING
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewCollectionStepTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @Test
  fun `collects a complete durable view value`() {
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "CollectedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
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
                val QUERY: SelectQuery<Author, *> = error("compile-only")
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
                val DEFINITION: SelectQuery<Author, *> = error("compile-only")
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
      encoderInputNullable = false,
      serializedTypeCanBeNull = false,
      decoderResultNullable = false,
      decoderInputNullable = false
    )
    val detailsConstruction = ModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(
        mockPropertyPath("details", "postalCode"),
        mockPropertyPath("details", "cityName")
      ),
      defaultableParameters = emptySet()
    )
    val expected = mockViewElement(
      parsedType = mockParsedType(typeName = authorSummaryType),
      identity = SqliteSchemaIdentity(
        schema = MAIN,
        identifier = SqliteIdentifier("author_summary")
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
      queryPropertyName = "DEFINITION",
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
          target = GenerationNames("Author")
        ),
        mockViewProjectionPropertyElement(
          access = mockPropertyAccess(path = mockPropertyPath("summary")),
          deserializedType = mockParsedType(typeName = innerSummaryType.copy(nullable = true)),
          isNullable = true,
          selectionKey = "summary",
          target = GenerationNames("InnerSummary")
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
  fun `retains complete originating files for a current-round view graph`() {
    val snapshots = mutableMapOf<String, OriginatingFilesSnapshot>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OriginRow.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class OriginRow(@Id val id: Long)
          """
        ),
        SourceFile.kotlin(
          name = "Email.kt",
          contents = """
            package $PACKAGE

            data class Email(val value: String)
          """
        ),
        SourceFile.kotlin(
          name = "EmailTransformer.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            @ObjectToDbValue
            fun emailToDb(value: Email): String = value.value

            @DbValueToObject
            fun emailFromDb(value: String): Email = Email(value)
          """
        ),
        SourceFile.kotlin(
          name = "Label.kt",
          contents = """
            package $PACKAGE

            data class Label(val value: String)
          """
        ),
        SourceFile.kotlin(
          name = "LabelTransformer.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            @ObjectToDbValue
            fun labelToDb(value: Label): String = value.value

            @DbValueToObject
            fun labelFromDb(value: String): Label = Label(value)
          """
        ),
        SourceFile.kotlin(
          name = "OriginInnerView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            data class OriginInnerView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
        SourceFile.kotlin(
          name = "OriginOuterView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            data class OriginOuterView(
              @ViewColumn("row") val row: OriginRow,
              @ViewColumn("other_row") val otherRow: OriginRow,
              @ViewColumn("email") val email: Email,
              @ViewColumn("other_email") val otherEmail: Email,
              @ViewColumn("label") val label: Label,
              @ViewColumn("other_label") val otherLabel: Label,
              @ViewColumn("inner") val inner: OriginInnerView
            ) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).insertAfter<ViewCollectionStep>(
            viewOriginRecordingStep(
              environment = environment,
              snapshots = snapshots
            )
          )
        }
      )
      .isOk()

    assertThat(snapshots.getValue("OriginOuterView"))
      .isEqualTo(
        OriginatingFilesSnapshot(
          files = setOf(
            "OriginOuterView.kt",
            "OriginRow.kt",
            "OriginInnerView.kt",
            "Email.kt",
            "EmailTransformer.kt",
            "Label.kt",
            "LabelTransformer.kt"
          ),
          isComplete = true
        )
      )
  }

  @Test
  fun `marks a later generated view over an earlier view as aggregating`() {
    val snapshots = mutableMapOf<String, OriginatingFilesSnapshot>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EarlierOriginView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            data class EarlierOriginView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment)
            .insertAfter<ViewCollectionStep>(
              viewOriginRecordingStep(
                environment = environment,
                snapshots = snapshots
              )
            )
            .insertBeforeLast(
              GeneratedSourceStep(
                environment = environment,
                fileName = "LaterOriginView",
                contents = """
                  package $PACKAGE

                  import com.siimkinks.sqlitemagic.SelectQuery
                  import com.siimkinks.sqlitemagic.annotation.View
                  import com.siimkinks.sqlitemagic.annotation.ViewColumn
                  import com.siimkinks.sqlitemagic.annotation.ViewQuery

                  @View
                  data class LaterOriginView(@ViewColumn("inner") val inner: EarlierOriginView) {
                    companion object {
                      @ViewQuery
                      val QUERY: SelectQuery<String, *> = error("compile-only")
                    }
                  }
                """
              )
            )
        }
      )
      .isOk()

    assertThat(snapshots)
      .containsExactly(
        "EarlierOriginView",
        OriginatingFilesSnapshot(
          files = setOf("EarlierOriginView.kt"),
          isComplete = true
        ),
        "LaterOriginView",
        OriginatingFilesSnapshot(
          files = setOf("LaterOriginView.kt", "EarlierOriginView.kt"),
          isComplete = false
        )
      )
  }
}

private fun viewOriginRecordingStep(
  environment: Environment,
  snapshots: MutableMap<String, OriginatingFilesSnapshot>
) = OriginRecordingStep(snapshots = snapshots) {
  environment.viewRoundElementsForCurrentRound.associate { roundElement ->
    roundElement.view.modelName to roundElement.originatingFiles
  }
}
