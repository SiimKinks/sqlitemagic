package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_NAME
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.processing.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.utils.NameConst
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class DatabaseConfigurationCollectionStepTest : ProcessingStepsTest {
  override val processingSteps
    get() = { env: Environment ->
      listOf(DatabaseConfigurationCollectionStep(env))
    }

  @Test
  fun `continues when database configuration annotations are absent`() {
    SqliteMagicCompilation
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
  fun `collects database name and version`() {
    SqliteMagicCompilation
      .compile(
        database(name = "main.db", version = 5)
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = "main.db",
            dbVersion = 5
          )
        )
        assertThat(environment.isSubmodule).isFalse()
        assertThat(environment.hasSubmodules).isFalse()
      }
  }

  @Test
  fun `annotation database name and version override compiler options`() {
    SqliteMagicCompilation
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
      }
  }

  @Test
  fun `keeps compiler option version when annotation version is default`() {
    SqliteMagicCompilation
      .compile(
        database(),
        kspOptions = mapOf(OPTION_DB_VERSION to "4")
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = null,
            dbVersion = 4
          )
        )
      }
  }

  @Test
  fun `keeps compiler option version for debug variant`() {
    SqliteMagicCompilation
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
  fun `keeps compiler option database name when annotation name is empty`() {
    SqliteMagicCompilation
      .compile(
        database(),
        kspOptions = mapOf(OPTION_DB_NAME to "option.db")
      )
      .isOk()
      .apply {
        assertThat(environment.dbMetadata).isEqualTo(
          DatabaseMetadata(
            dbName = "option.db",
            dbVersion = null
          )
        )
      }
  }

  @Test
  fun `collects submodule name`() {
    SqliteMagicCompilation
      .compile(
        submoduleDatabase(moduleName = "feature")
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleName).isEqualTo("Feature")
        assertThat(environment.isSubmodule).isTrue()
      }
  }

  @Test
  fun `collects database submodule manager references`() {
    val submoduleCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(className = "FeatureConfig", moduleName = "feature"),
        generatedClassesManager("FeatureGeneratedClassesManager")
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        database(submodules = listOf("FeatureConfig")),
        classpaths = listOf(submoduleCompilation.result.outputDirectory)
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleDatabases).isEqualTo(
          listOf(
            SubmoduleDatabaseMetadata(
              moduleName = "Feature",
              managerQualifiedName = "${NameConst.PACKAGE_ROOT}.FeatureGeneratedClassesManager"
            )
          )
        )
        assertThat(environment.hasSubmodules).isTrue()
      }
  }

  @Test
  fun `collects database submodule manager name when manager is not resolved yet`() {
    val submoduleCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(className = "FeatureConfig", moduleName = "feature")
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        database(submodules = listOf("test.FeatureConfig")),
        classpaths = listOf(submoduleCompilation.result.outputDirectory)
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleDatabases).isEqualTo(
          listOf(
            SubmoduleDatabaseMetadata(
              moduleName = "Feature",
              managerQualifiedName = "${NameConst.PACKAGE_ROOT}.FeatureGeneratedClassesManager"
            )
          )
        )
        assertThat(environment.hasSubmodules).isTrue()
      }
  }

  @Test
  fun `collects multiple database submodule manager references`() {
    val featureCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(
          className = "FeatureConfig",
          moduleName = "feature"
        )
      )
      .isOk()
    val billingCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(
          className = "BillingConfig",
          moduleName = "billing"
        )
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        database(
          submodules = listOf(
            "test.FeatureConfig",
            "test.BillingConfig"
          )
        ),
        classpaths = listOf(
          featureCompilation.result.outputDirectory,
          billingCompilation.result.outputDirectory
        )
      )
      .isOk()
      .apply {
        assertThat(environment.submoduleDatabases).isEqualTo(
          listOf(
            SubmoduleDatabaseMetadata(
              moduleName = "Feature",
              managerQualifiedName = "${NameConst.PACKAGE_ROOT}.FeatureGeneratedClassesManager"
            ),
            SubmoduleDatabaseMetadata(
              moduleName = "Billing",
              managerQualifiedName = "${NameConst.PACKAGE_ROOT}.BillingGeneratedClassesManager"
            )
          )
        )
        assertThat(environment.hasSubmodules).isTrue()
      }
  }

  @Test
  fun `fails when more than one database is annotated`() {
    SqliteMagicCompilation
      .compile(
        database(className = "FirstDatabase"),
        database(className = "SecondDatabase")
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Only one element per module can be annotated with @Database")
  }

  @Test
  fun `fails when more than one submodule database is annotated`() {
    SqliteMagicCompilation
      .compile(
        submoduleDatabase(className = "FirstSubmodule", moduleName = "first"),
        submoduleDatabase(className = "SecondSubmodule", moduleName = "second")
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Only one element per module can be annotated with @SubmoduleDatabase")
  }

  @Test
  fun `fails when database and submodule database are both annotated`() {
    SqliteMagicCompilation
      .compile(
        database(),
        submoduleDatabase(moduleName = "feature")
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Module can have either @Database or @SubmoduleDatabase annotated element, but not both")
  }

  @Test
  fun `fails when submodule name is empty`() {
    SqliteMagicCompilation
      .compile(
        submoduleDatabase(moduleName = "")
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Submodule name cannot be empty or null")
  }

  @Test
  fun `fails when referenced database submodule name is empty`() {
    val submoduleCompilation = SqliteMagicCompilation
      .compile(
        submoduleDatabase(className = "FeatureConfig", moduleName = ""),
        processingStepsFactory = { emptyList() }
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        database(submodules = listOf("test.FeatureConfig")),
        classpaths = listOf(submoduleCompilation.result.outputDirectory)
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Submodule name cannot be empty")
  }

  @Test
  fun `fails when database submodule reference is not annotated`() {
    SqliteMagicCompilation
      .compile(
        plainClass("FeatureConfig"),
        database(submodules = listOf("test.FeatureConfig"))
      )
      .hasExitCode(COMPILATION_ERROR)
      .hasMessage("Database submodule test.FeatureConfig must be annotated with @SubmoduleDatabase")
  }

  //region helpers
  private fun database(
    className: String = "TestDatabase",
    name: String? = null,
    version: Int? = null,
    submodules: List<String> = emptyList()
  ) = SourceFile.kotlin(
    "$className.kt",
    """
      package test

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(${databaseArguments(name, version, submodules)})
      class $className
      """.trimIndent()
  )

  private fun submoduleDatabase(
    className: String = "TestSubmoduleDatabase",
    moduleName: String
  ) = SourceFile.kotlin(
    "$className.kt",
    """
      package test

      import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase

      @SubmoduleDatabase("$moduleName")
      class $className
      """.trimIndent()
  )

  private fun generatedClassesManager(className: String) = SourceFile.kotlin(
    "$className.kt",
    """
      package ${NameConst.PACKAGE_ROOT}

      class $className
      """.trimIndent()
  )

  private fun plainClass(className: String) = SourceFile.kotlin(
    "$className.kt",
    """
      package test

      class $className
      """.trimIndent()
  )

  private fun databaseArguments(
    name: String?,
    version: Int?,
    submodules: List<String>
  ): String = buildList {
    if (name != null) {
      add("name = \"$name\"")
    }
    if (version != null) {
      add("version = $version")
    }
    if (submodules.isNotEmpty()) {
      add("submodules = [${submodules.joinToString { "$it::class" }}]")
    }
  }.joinToString()
  //endregion
}
