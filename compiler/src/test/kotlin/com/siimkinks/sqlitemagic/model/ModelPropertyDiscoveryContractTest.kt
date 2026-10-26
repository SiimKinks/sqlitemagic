package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelPropertyDiscoveryContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `persists only discovered Kotlin properties in inherited-before-local order`() {
    compiler
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
        ),
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
        ),
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
        ),
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
      .withGeneratedSource("SqliteMagic_CompanionProperty_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("instance_value")
        generatedSource.assertDoesNotContain("companion_value")
      }
      .withGeneratedSource("SqliteMagic_SelectiveMutableKotlin_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("persisted_value")
        generatedSource.assertDoesNotContain("ignored_value")
      }
      .withGeneratedSource("SqliteMagic_InheritedPropertyOrder_Adapter.kt") {
        it.assertContainsInOrder(
          "inherited_first",
          "inherited_second",
          "local_first",
          "local_second"
        )
      }
      .withGeneratedSource("SqliteMagic_CalculatedValues_Adapter.kt") {
        it.assertDoesNotContain(
          "computed",
          "delegated"
        )
      }
  }
}
