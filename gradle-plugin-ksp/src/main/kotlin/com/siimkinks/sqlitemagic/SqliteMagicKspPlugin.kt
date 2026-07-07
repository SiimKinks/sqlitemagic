package com.siimkinks.sqlitemagic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspExtension
import com.siimkinks.sqlitemagic.structure.MigrationsHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.util.Locale

const val DB_TASK_GROUP = "db"

class SqliteMagicKspPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val sqlitemagic = project.extensions.create(
      "sqlitemagic",
      SqliteMagicKspPluginExtension::class.java
    )
    configureProject(project, sqlitemagic)

    project.plugins.withType(AppPlugin::class.java) {
      project
        .extensions
        .getByType(AndroidComponentsExtension::class.java)
        .onVariants { variant ->
          project.configureKspVariantArgs(variant)
          if (!variant.isDebug) {
            variant.addMigrateDbTask(project)
          }
        }
    }
    project.plugins.withType(LibraryPlugin::class.java) {
      project
        .extensions
        .getByType(AndroidComponentsExtension::class.java)
        .onVariants(
          callback = project::configureKspVariantArgs
        )
    }
  }

  private fun configureProject(
    project: Project,
    sqlitemagic: SqliteMagicKspPluginExtension
  ) {
    project.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
      override fun beforeEvaluate(project: Project) {}

      override fun afterEvaluate(project: Project, state: ProjectState) {
        project.gradle.removeProjectEvaluationListener(this)

        project.configureKspArgs(sqlitemagic)
        if (!sqlitemagic.configureAutomatically) {
          return
        }

        val implDeps = project.getConfiguration(depName = "implementation", fallback = "compile")
        val compileOnlyDeps = project.getConfiguration(depName = "compileOnly", fallback = "provided")

        project
          .getConfiguration("ksp")
          .addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-compiler-ksp:$PLUGIN_VERSION")
        compileOnlyDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$PLUGIN_VERSION")
        implDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic:$PLUGIN_VERSION")
        implDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-kotlin:$PLUGIN_VERSION")
      }
    })
  }
}

private fun Project.configureKspArgs(sqlitemagic: SqliteMagicKspPluginExtension) {
  val ksp = extensions.getByType(KspExtension::class.java)
  ksp.arg("sqlitemagic.kotlin.public.extensions", sqlitemagic.publicKotlinExtensionFunctions.toString())
  ksp.arg("sqlitemagic.generate.logging", sqlitemagic.generateLogging.toString())
  ksp.arg("sqlitemagic.migrate.debug", sqlitemagic.migrateDebugDatabase.toString())
  ksp.arg("sqlitemagic.project.dir", projectDir.absolutePath)
  sqlitemagic.mainModulePath?.let { mainModulePath ->
    ksp.arg(
      "sqlitemagic.main.module.path",
      File(rootDir, mainModulePath).absolutePath
    )
  }
  if (sqlitemagic.debug) {
    ksp.arg("sqlitemagic.ksp.debug", "true")
  }
}

private fun Project.configureKspVariantArgs(variant: Variant) {
  val variantNameArg = variant.name
  val variantDebugArg = variant.isDebug.toString()
  tasks.withType(KspAATask::class.java).configureEach { task ->
    if (task.name == variant.kspTaskName()) {
      task.kspConfig.apOptions.put("sqlitemagic.variant.name", variantNameArg)
      task.kspConfig.apOptions.put("sqlitemagic.variant.debug", variantDebugArg)
      task.kspConfig.processorOptions.put("sqlitemagic.variant.name", variantNameArg)
      task.kspConfig.processorOptions.put("sqlitemagic.variant.debug", variantDebugArg)
    }
  }
}

private fun Variant.addMigrateDbTask(project: Project) {
  val buildTypeName = buildType ?: return
  val taskName = "migrate${name.capitalize()}Db"
  val migrationTask = project.tasks.register(taskName) {
    it.doFirst {
      val projectDir = project.projectDir
      val dbDir = File(projectDir, "db")
      check(dbDir.exists()) {
        "Database metadata directory must exist in order to create migrations. Build project and try again…"
      }

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

fun Project.getConfiguration(depName: String, fallback: String = ""): Configuration =
  try {
    configurations.getByName(depName)
  } catch (_: Throwable) {
    configurations.getByName(fallback)
  }

fun Configuration.addDependency(project: Project, dependency: String) {
  dependencies.add(project.dependencies.create(dependency))
}

private fun Variant.kspTaskName(): String = "ksp${name.capitalize()}Kotlin"

private fun String.capitalize() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.getDefault())
    else -> it.toString()
  }
}

private val ComponentIdentity.isDebug: Boolean
  get() = buildType == "debug"
