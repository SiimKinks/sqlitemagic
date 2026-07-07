package com.siimkinks.sqlitemagic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.siimkinks.sqlitemagic.structure.MigrationsHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.util.*

const val DB_TASK_GROUP = "db"

class SqliteMagicPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val sqlitemagic = project.extensions.create("sqlitemagic", SqliteMagicPluginExtension::class.java)
    configureProject(project, sqlitemagic)

    project.plugins.withType(AppPlugin::class.java) {
      project
        .extensions
        .getByType(AndroidComponentsExtension::class.java)
        .onVariants { variant ->
          configureAptArgs(project, sqlitemagic, variant)
          if (!variant.isDebug) {
            variant.addMigrateDbTask(project)
          }
        }
    }
    project.plugins.withType(LibraryPlugin::class.java) {
      project
        .extensions
        .getByType(AndroidComponentsExtension::class.java)
        .onVariants { variant ->
          configureAptArgs(project, sqlitemagic, variant)
        }
    }
  }

  private fun configureProject(project: Project, sqlitemagic: SqliteMagicPluginExtension) {
    project.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
      override fun beforeEvaluate(project: Project) {}

      override fun afterEvaluate(project: Project, state: ProjectState) {
        project.gradle.removeProjectEvaluationListener(this)
        if (!sqlitemagic.configureAutomatically) {
          return
        }

        val compileDeps = project.getConfiguration(depName = "implementation", fallback = "compile")
        val providedDeps = project.getConfiguration(depName = "compileOnly", fallback = "provided")

        val kotlinProject = project.extensions.findByName("kotlin") != null || project.hasAnyPlugin(
          "kotlin-android",
          "kotlin",
          "org.jetbrains.kotlin.android",
          "org.jetbrains.kotlin.jvm",
          "com.android.built-in-kotlin"
        )

        val compilerArtifact = when {
          !sqlitemagic.generateMagicMethods -> "sqlitemagic-compiler"
          sqlitemagic.useKotlin && kotlinProject -> "sqlitemagic-compiler-kotlin"
          else -> "sqlitemagic-compiler-magic"
        }

        val hasKpt = project.hasAnyPlugin(
          "kotlin-kapt",
          "org.jetbrains.kotlin.kapt",
          "com.android.legacy-kapt"
        )
        if (sqlitemagic.useKotlin && kotlinProject && !hasKpt) {
          throw IllegalStateException("Missing kotlin kapt plugin! Set sqlitemagic.useKotlin=false or add kotlin-kapt plugin")
        }

        project.getConfiguration(if (hasKpt) "kapt" else "annotationProcessor")
          .addDependency(project, "com.siimkinks.sqlitemagic:$compilerArtifact:$PLUGIN_VERSION")
        providedDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$PLUGIN_VERSION")
        compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic:$PLUGIN_VERSION")
        if (sqlitemagic.useKotlin && kotlinProject) {
          compileDeps.addDependency(
            project,
            "com.siimkinks.sqlitemagic:sqlitemagic-kotlin:$PLUGIN_VERSION"
          )
        }
      }
    })
  }

  private fun configureAptArgs(
    project: Project,
    sqlitemagic: SqliteMagicPluginExtension,
    variant: Variant
  ) {
    variant.addAptArg("sqlitemagic.generate.logging", sqlitemagic.generateLogging)
    variant.addAptArg("sqlitemagic.auto.lib", sqlitemagic.autoValueAnnotation)
    variant.addAptArg(
      "sqlitemagic.kotlin.public.extensions",
      sqlitemagic.publicKotlinExtensionFunctions
    )
    variant.addAptArg("sqlitemagic.migrate.debug", sqlitemagic.migrateDebugDatabase)
    variant.addAptArg("sqlitemagic.project.dir", project.projectDir)
    variant.addAptArg("sqlitemagic.variant.name", variant.name)
    variant.addAptArg("sqlitemagic.variant.debug", variant.isDebug)
    sqlitemagic.mainModulePath?.let { mainModulePath ->
      variant.addAptArg(
        "sqlitemagic.main.module.path",
        File(project.rootDir, mainModulePath).absolutePath
      )
    }
  }

  private fun Variant.addMigrateDbTask(project: Project) {
    val buildTypeName = buildType ?: return
    val taskName = "migrate${name.capitalize()}Db"
    val migrationTask = project.tasks.register(taskName) {
      it.doFirst {
        val projectDir = project.projectDir
        val dbDir = File(projectDir, "db")
        check(dbDir.exists()) { "Database metadata directory must exist in order to create migrations. Build project and try again…" }

        MigrationsHandler.handleReleaseMigrations(
          projectDir = projectDir,
          dbDir = dbDir,
          variantName = buildTypeName
        )
      }
    }
    migrationTask.configure {
      it.group = DB_TASK_GROUP
    }
  }
}

fun Project.getConfiguration(depName: String, fallback: String = ""): Configuration =
  try {
    configurations.getByName(depName)
  } catch (_: Throwable) {
    configurations.getByName(fallback)
  }

fun Configuration.addDependency(project: Project, dependency: String) {
  dependencies.add(project.dependencies.create(dependency))
}

fun Project.hasAnyPlugin(vararg pluginIds: String): Boolean =
  pluginIds.firstNotNullOfOrNull(this.plugins::findPlugin) != null

private fun Variant.addAptArg(name: String, value: Any) {
  javaCompilation
    ?.annotationProcessor
    ?.arguments
    ?.put(name, value.toString())
}

private fun String.capitalize() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.getDefault())
    else -> it.toString()
  }
}

private val ComponentIdentity.isDebug: Boolean
  get() = buildType == "debug"
