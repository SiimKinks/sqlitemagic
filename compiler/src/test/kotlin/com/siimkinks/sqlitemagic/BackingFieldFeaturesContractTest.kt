package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSBackingField
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.siimkinks.sqlitemagic.AnnotationNames.COLUMN_ANNOTATION
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ConsumedAnnotations
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.normalizePropertySymbol
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.junit.jupiter.api.Test
import java.io.File

internal class BackingFieldFeaturesContractTest {
  @Test
  fun `normalizes explicit backing fields while retaining exposed property types and field annotations`() {
    val probe = object : ProcessingStep {
      val snapshots: List<BackingFieldSnapshot>
        field = mutableListOf()

      override fun process(resolver: Resolver): Continue {
        val annotations = ConsumedAnnotations()
        resolver
          .getSymbolsWithAnnotation(COLUMN_ANNOTATION)
          .forEach { symbol ->
            val property = symbol.normalizePropertySymbol() as KSPropertyDeclaration
            val backingField = requireNotNull(property.backingField)
            snapshots += BackingFieldSnapshot(
              resolverReturnsField = symbol is KSBackingField,
              propertyName = property.simpleName.asString(),
              propertyType = property.type
                .resolve()
                .declaration
                .qualifiedName
                ?.asString(),
              fieldType = backingField.type
                .resolve()
                .declaration
                .qualifiedName
                ?.asString(),
              columnFromProperty = annotations.column(property)?.value,
              columnFromField = annotations.column(backingField)?.value,
              propertyIsUnique = annotations.unique(property) != null,
              fieldIsUnique = annotations.unique(backingField) != null
            )
          }
        return Continue
      }
    }
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ExplicitBackingFieldRecord.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Unique

            class ExplicitBackingFieldRecord {
              @Column("amount")
              @Unique
              val amountValue: Number
                field: Long = 0L
            }
          """
        ),
        processingStepsFactory = { listOf(probe) }
      )
      .apply {
        assertWithMessage(result.messages)
          .that(result.exitCode)
          .isEqualTo(OK)
      }

    assertThat(probe.snapshots).containsExactly(
      BackingFieldSnapshot(
        resolverReturnsField = true,
        propertyName = "amountValue",
        propertyType = "kotlin.Number",
        fieldType = "kotlin.Long",
        columnFromProperty = "amount",
        columnFromField = "amount",
        propertyIsUnique = true,
        fieldIsUnique = true
      )
    )
    assertThat(compilation.result.messages).doesNotContain("has not opted in for upcoming features")
  }

  @Test
  fun `production provider opts in and preserves field annotations`() {
    val result = KotlinCompilation()
      .apply {
        useKsp2()
        jvmTarget = requireNotNull(System.getProperty("sqlitemagic.test.jvmTarget"))
        inheritClassPath = true
        symbolProcessorProviders = mutableListOf(SqliteMagicSymbolProcessorProvider())
        sources = listOf(
          SourceFile.kotlin(
            name = "ProductionBackingFields.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Index
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              data class ProductionBackingFields(
                @Id val id: String,
                @Column("display_name") @Index("display_lookup") val name: String
              )
            """
          )
        )
      }
      .compile()
      .apply { isOk() }

    assertThat(result.messages).doesNotContain("has not opted in for upcoming features")
    result.sourcesGeneratedBySymbolProcessor
      .single { it.name == "SqliteMagic_ProductionBackingFields_Adapter.kt" }
      .let(File::readText)
      .assertContains("id TEXT PRIMARY KEY", "display_name TEXT")
    result.sourcesGeneratedBySymbolProcessor
      .single { it.name == "SqliteMagicDatabase.kt" }
      .let(File::readText)
      .assertContains("display_lookup", "display_name")
  }
}

private data class BackingFieldSnapshot(
  val resolverReturnsField: Boolean,
  val propertyName: String,
  val propertyType: String?,
  val fieldType: String?,
  val columnFromProperty: String?,
  val columnFromField: String?,
  val propertyIsUnique: Boolean,
  val fieldIsUnique: Boolean
)

