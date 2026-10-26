package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.ProcessorCompilationResult
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNotGeneratedSources
import com.siimkinks.sqlitemagic.utils.insertAfter
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URLClassLoader

internal class TablePositionContractTest {
  private val compiler = SqliteMagicCompiler(::genClassesManagerProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "main, false",
    "submodule, true"
  )
  fun `cached positions agree with manager in either initialization order`(
    label: String,
    submodule: Boolean
  ) {
    val databaseAnnotation = when {
      submodule -> """@SubmoduleDatabase("Feature")"""
      else -> "@Database"
    }
    val managerName = when {
      submodule -> "FeatureGeneratedClassesManager"
      else -> "SqliteMagicDatabase"
    }
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "Positions.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.annotation.*
            $databaseAnnotation class PositionDatabase
            @Table data class UpperCase(val value: String)
            class Outer {
              @Table class Nested { var value: String = "" }
            }
            @Table(options = [TableOption.TEMPORARY]) data class Temporary(val value: String)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "$managerName.kt",
        "SqliteMagic_UpperCase_Adapter.kt",
        "SqliteMagic_Outer_Nested_Adapter.kt",
        "SqliteMagic_Temporary_Adapter.kt"
      )
    val tables = compilation.environment.tableElements.values.toList()
    assertThat(tables.map(TableElement::artifactStem)).containsExactly("UpperCase", "Outer_Nested", "Temporary")
    compilation.withGeneratedSource("$managerName.kt") { managerSource ->
      assertThat(managerSource.contains("companion object")).isEqualTo(!submodule)
      tables.forEach { table ->
        managerSource.assertContains("val tablePosition_${table.artifactStem}: Int = ${table.declarationOrder}")
      }
    }
    tables.forEach { table ->
      compilation.withGeneratedSource("${table.generationNames.adapterClassName.simpleName}.kt") {
        it.assertContains(
          "override val tablePosition: Int = $managerName.tablePosition_${table.artifactStem}"
        )
      }
    }
    val outputUrl = compilation.outputDirectory
      .toURI()
      .toURL()
    listOf("adapter-first" to false, "manager-first" to true).forEach { (order, managerFirst) ->
      val caseLabel = "$label $order"
      URLClassLoader(
        arrayOf(outputUrl),
        javaClass.classLoader
      ).use { loader ->
        val manager by lazy(LazyThreadSafetyMode.NONE) {
          val type = loader.loadClass("com.siimkinks.sqlitemagic.$managerName")
          when {
            submodule -> type.getField("INSTANCE")
              .get(null)
            else -> type.getField("Companion")
              .get(null)
          }
        }
        if (managerFirst) {
          // Force manager-first initialization order; adapter-first stays lazy below.
          manager
        }
        val positions = tables.map { table ->
          val adapterType = loader.loadClass(table.generationNames.adapterClassName.canonicalName)
          val adapter = adapterType.getField("INSTANCE")
            .get(null)
          val actual = adapterType.getMethod("getTablePosition")
            .invoke(adapter) as Int
          val registered = (manager.javaClass.enclosingClass ?: manager.javaClass)
            .getField("tablePosition_${table.artifactStem}")
            .get(null)
          assertWithMessage("$caseLabel ${table.artifactStem}")
            .that(actual)
            .isEqualTo(registered)
          assertWithMessage("$caseLabel ${table.artifactStem}")
            .that(actual)
            .isEqualTo(table.declarationOrder)
          actual
        }
        assertWithMessage(caseLabel)
          .that(positions)
          .containsExactlyElementsIn(tables.indices)
          .inOrder()
        val managerType = loader.loadClass("com.siimkinks.sqlitemagic.$managerName")
        val database = when {
          submodule -> managerType.getField("INSTANCE")
            .get(null)
          else -> managerType.getConstructor()
            .newInstance()
        }
        val count = managerType
          .getMethod("getNrOfTables", String::class.java)
          .invoke(database, null)
        assertWithMessage(caseLabel)
          .that(count)
          .isEqualTo(tables.size)
      }
    }
  }

  @Test
  fun `clean builds with and without an earlier nested table shift positions and preserve surviving adapters`() {
    val withoutEarlier = compileMembership(earlier = false)
    val withEarlier = compileMembership(earlier = true)
    val survivors = withoutEarlier.environment.tableElements.values.toList()
    assertThat(withEarlier.environment.tableElements.values.first().artifactStem).isEqualTo("Outer_Earlier")
    survivors.forEach { table ->
      val shifted = checkNotNull(withEarlier.environment.tableElements[table.typeKey])
      assertThat(shifted.declarationOrder).isEqualTo(table.declarationOrder + 1)
      val fileName = "${table.generationNames.adapterClassName.simpleName}.kt"
      assertThat(withEarlier.generatedSource(fileName)).isEqualTo(withoutEarlier.generatedSource(fileName))
    }
    listOf(withoutEarlier, withEarlier).forEach(::assertRegistry)
  }

  @Test
  fun `generated nonpersisted direct helper changes readiness order safely`() {
    val helperSource = """
      package $PACKAGE

      class GeneratedHelper
    """
    val helper = SourceFile.kotlin(
      name = "GeneratedHelper.kt",
      contents = helperSource
    )
    val models = SourceFile.kotlin(
      name = "Readiness.kt",
      contents = """
        package $PACKAGE
        import com.siimkinks.sqlitemagic.annotation.Id
        import com.siimkinks.sqlitemagic.annotation.Table
        open class HelperBase
        @Table class Earlier : HelperBase() {
          var later: Later? = null
          fun helper(value: GeneratedHelper): GeneratedHelper = value
        }
        @Table data class Later(@Id val id: Long, val value: String)
      """
    )
    val ready = compiler
      .compile(models, helper)
      .isOk()
      .assertGeneratedSources(
        "SqliteMagicDatabase.kt",
        "SqliteMagic_Earlier_Adapter.kt",
        "SqliteMagic_Later_Adapter.kt"
      )
    val delayed = SqliteMagicCompilation
      .compile(
        models,
        processingStepsFactory = { environment ->
          genClassesManagerProcessingSteps(environment)
            .insertAfter<ModelCollectionStep>(
              GeneratedSourceStep(
                environment = environment,
                fileName = "GeneratedHelper",
                contents = helperSource
              )
            )
        }
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagicDatabase.kt",
        "SqliteMagic_Earlier_Adapter.kt",
        "SqliteMagic_Later_Adapter.kt"
      )
    val readyOrder = ready.environment.tableElements.values.map(TableElement::artifactStem)
    val delayedOrder = delayed.environment.tableElements.values.map(TableElement::artifactStem)
    assertThat(readyOrder).containsExactly("Earlier", "Later").inOrder()
    assertThat(delayedOrder).containsExactly("Later", "Earlier").inOrder()
    listOf("ready" to ready, "delayed" to delayed).forEach { (label, compilation) ->
      assertRegistry(compilation)
      compilation.withGeneratedSource("SqliteMagicDatabase.kt") { manager ->
        manager.assertContains(
          "SqliteMagic_Later_Adapter.TABLE_SCHEMA",
          "SqliteMagic_Earlier_Adapter.TABLE_SCHEMA"
        )
        assertWithMessage(label)
          .that(manager.indexOf("SqliteMagic_Later_Adapter.TABLE_SCHEMA"))
          .isLessThan(manager.indexOf("SqliteMagic_Earlier_Adapter.TABLE_SCHEMA"))
      }
    }
    listOf("Earlier", "Later").forEach { stem ->
      val fileName = "SqliteMagic_${stem}_Adapter.kt"
      assertThat(delayed.generatedSource(fileName)).isEqualTo(ready.generatedSource(fileName))
    }
  }

  private fun compileMembership(earlier: Boolean): ProcessorCompilationResult {
    val nested = when {
      earlier -> "class Outer { @Table data class Earlier(val value: String) }"
      else -> "class Outer"
    }
    val expectedStems = when {
      earlier -> listOf("Outer_Earlier", "Survivor", "Last")
      else -> listOf("Survivor", "Last")
    }
    return compiler
      .compile(
        SourceFile.kotlin(
          name = "Membership.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.annotation.Table
            $nested
            @Table data class Survivor(val value: String)
            @Table data class Last(val value: String)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagicDatabase.kt",
        "SqliteMagic_Survivor_Adapter.kt",
        "SqliteMagic_Last_Adapter.kt"
      )
      .apply {
        assertThat(environment.tableElements.values.map(TableElement::artifactStem))
          .containsExactlyElementsIn(expectedStems)
          .inOrder()
        when {
          earlier -> assertGeneratedSources("SqliteMagic_Outer_Earlier_Adapter.kt")
          else -> assertNotGeneratedSources("SqliteMagic_Outer_Earlier_Adapter.kt")
        }
      }
  }

  private fun assertRegistry(compilation: ProcessorCompilationResult) {
    val tables = compilation.environment.tableElements.values.toList()
    assertThat(tables.map(TableElement::declarationOrder)).containsExactlyElementsIn(tables.indices).inOrder()
    compilation.withGeneratedSource("SqliteMagicDatabase.kt") { manager ->
      tables.forEach { table ->
        manager.assertContains("val tablePosition_${table.artifactStem}: Int = ${table.declarationOrder}")
      }
    }
  }
}
