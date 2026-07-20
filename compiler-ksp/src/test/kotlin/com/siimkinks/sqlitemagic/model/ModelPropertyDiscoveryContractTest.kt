package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelPropertyDiscoveryContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `does not persist Kotlin companion properties`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CompanionProperty.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class CompanionProperty {
              var instanceValue: String = ""

              companion object {
                var companionValue: String = ""
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_CompanionProperty_Handler.kt")
      .withGeneratedSource("SqliteMagic_CompanionProperty_Handler.kt") { generatedSource ->
        generatedSource.assertContains("instance_value")
        generatedSource.assertDoesNotContain("companion_value")
      }
  }

  @Test
  fun `applies persistAll false to mutable Kotlin models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "SelectiveMutableKotlin.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            class SelectiveMutableKotlin {
              @Column
              var persistedValue: String = ""
              var ignoredValue: String = ""
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_SelectiveMutableKotlin_Handler.kt")
      .withGeneratedSource("SqliteMagic_SelectiveMutableKotlin_Handler.kt") { generatedSource ->
        generatedSource.assertContains("persisted_value")
        generatedSource.assertDoesNotContain("ignored_value")
      }
  }

  @Test
  fun `preserves inherited-before-local mutable property order`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InheritedPropertyOrder.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            open class OrderedBase {
              var inheritedFirst: String = ""
              var inheritedSecond: Long = 0
            }

            @Table
            class InheritedPropertyOrder : OrderedBase() {
              var localFirst: Double = 0.0
              var localSecond: String = ""
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_InheritedPropertyOrder_Handler.kt")
      .withGeneratedSource("SqliteMagic_InheritedPropertyOrder_Handler.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "inherited_first",
          "inherited_second",
          "local_first",
          "local_second"
        )
      }
  }

  @Test
  fun `does not persist computed or delegated properties`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CalculatedValues.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class CalculatedValues(
              @Id
              val id: Long,
              val stored: String
            ) {
              val computed: String get() = stored.uppercase()
              val delegated: String by lazy(stored::lowercase)
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_CalculatedValues_Handler.kt")
      .withGeneratedSource("SqliteMagic_CalculatedValues_Handler.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "computed",
          "delegated"
        )
      }
  }
}
