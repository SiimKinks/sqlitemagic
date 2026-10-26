package com.siimkinks.sqlitemagic.dbconfig

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_NAME
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class DatabaseConfigurationCollectionStepTest {
  private val compiler = SqliteMagicCompiler(::databaseConfigurationProcessingSteps)

  @Test
  fun `continues when database configuration annotations are absent`() {
    compiler
      .compile(
        plainClass(className = "Unconfigured"),
        kspOptions = mapOf(
          OPTION_DB_NAME to "option.db",
          OPTION_DB_VERSION to "7"
        )
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = "option.db",
            dbVersion = 7
          )
        )
        assertThat(environment.isSubmodule).isFalse()
        assertThat(environment.hasSubmodules).isFalse()
      }
  }

  @Test
  fun `annotation database name and version override compiler options`() {
    compiler
      .compile(
        database(
          name = "annotation.db",
          version = 9
        ),
        kspOptions = mapOf(
          OPTION_DB_NAME to "option.db",
          OPTION_DB_VERSION to "3"
        )
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = "annotation.db",
            dbVersion = 9
          )
        )
        assertThat(environment.isSubmodule).isFalse()
        assertThat(environment.hasSubmodules).isFalse()
      }
  }

  @Test
  fun `keeps compiler option name and version when annotation values are defaults`() {
    compiler
      .compile(
        database(),
        kspOptions = mapOf(
          OPTION_DB_NAME to "option.db",
          OPTION_DB_VERSION to "4"
        )
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = "option.db",
            dbVersion = 4
          )
        )
      }
  }

  @Test
  fun `keeps compiler option version for debug variant`() {
    compiler
      .compile(
        database(version = 5),
        kspOptions = mapOf(
          OPTION_DB_VERSION to "3",
          OPTION_VARIANT_DEBUG to "true"
        )
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = null,
            dbVersion = 3
          )
        )
      }
  }

  @Test
  fun `collects submodule names and multiple database submodule manager references`() {
    val featureCompilation = compiler
      .compile(
        submoduleDatabase(
          className = "FeatureConfig",
          moduleName = "feature"
        )
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleName).isEqualTo("Feature")
        assertThat(environment.isSubmodule).isTrue()
      }
    val billingCompilation = compiler
      .compile(
        submoduleDatabase(
          className = "BillingConfig",
          moduleName = "billing"
        )
      )
      .isOk()

    compiler
      .compile(
        database(
          submodules = listOf(
            "test.FeatureConfig",
            "test.BillingConfig"
          )
        ),
        classpaths = listOf(
          featureCompilation.outputDirectory,
          billingCompilation.outputDirectory
        )
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleDatabases).isEqualTo(
          listOf(
            SubmoduleDatabaseMetadata(
              moduleName = "Feature",
              managerQualifiedName = "$PACKAGE_ROOT.FeatureGeneratedClassesManager"
            ),
            SubmoduleDatabaseMetadata(
              moduleName = "Billing",
              managerQualifiedName = "$PACKAGE_ROOT.BillingGeneratedClassesManager"
            )
          )
        )
        assertThat(environment.hasSubmodules).isTrue()
      }
  }

  @Test
  fun `fails when more than one database is annotated`() {
    compiler
      .compile(
        database(className = "FirstDatabase"),
        database(className = "SecondDatabase")
      )
      .assertCompilationError("Only one element per module can be annotated with @Database")
  }

  @Test
  fun `fails when more than one submodule database is annotated`() {
    compiler
      .compile(
        submoduleDatabase(
          className = "FirstSubmodule",
          moduleName = "first"
        ),
        submoduleDatabase(
          className = "SecondSubmodule",
          moduleName = "second"
        )
      )
      .assertCompilationError("Only one element per module can be annotated with @SubmoduleDatabase")
  }

  @Test
  fun `fails when database and submodule database are both annotated`() {
    compiler
      .compile(
        database(),
        submoduleDatabase(moduleName = "feature")
      )
      .assertCompilationError("Module can have either @Database or @SubmoduleDatabase annotated element, but not both")
  }

  @Test
  fun `fails when submodule name is empty`() {
    compiler
      .compile(
        submoduleDatabase(moduleName = "")
      )
      .assertCompilationError("Submodule name cannot be empty or null")
  }

  @Test
  fun `fails when referenced database submodule name is empty`() {
    val submoduleCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(
          className = "FeatureConfig",
          moduleName = ""
        ),
        processingStepsFactory = { emptyList() }
      )
      .isOk()

    compiler
      .compile(
        database(submodules = listOf("test.FeatureConfig")),
        classpaths = listOf(submoduleCompilation.outputDirectory)
      )
      .assertCompilationError("Submodule name cannot be empty")
  }

  @Test
  fun `fails when database submodule reference is not annotated`() {
    compiler
      .compile(
        plainClass("FeatureConfig"),
        database(submodules = listOf("test.FeatureConfig"))
      )
      .assertCompilationError("Database submodule test.FeatureConfig must be annotated with @SubmoduleDatabase")
  }

  private fun database(
    className: String = "TestDatabase",
    name: String? = null,
    version: Int? = null,
    submodules: List<String> = emptyList()
  ) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package test

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(${
      databaseArguments(
        name = name,
        version = version,
        submodules = submodules
      )
    })
      class $className
      """
  )

  private fun submoduleDatabase(
    className: String = "TestSubmoduleDatabase",
    moduleName: String
  ) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package test

      import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase

      @SubmoduleDatabase("$moduleName")
      class $className
      """
  )

  private fun plainClass(className: String) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package test

      class $className
      """
  )

  private fun databaseArguments(
    name: String?,
    version: Int?,
    submodules: List<String>
  ) = buildList {
    if (name != null) {
      add("""name = "$name"""")
    }
    if (version != null) {
      add("version = $version")
    }
    if (submodules.isNotEmpty()) {
      add("submodules = [${submodules.joinToString { "$it::class" }}]")
    }
  }.joinToString()
}

private fun databaseConfigurationProcessingSteps(environment: Environment): List<ProcessingStep> =
  listOf(DatabaseConfigurationCollectionStep(environment))
