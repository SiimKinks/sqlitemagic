package com.siimkinks.sqlitemagic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspExtension
import com.siimkinks.sqlitemagic.manager.ReleaseMigrationCoordinator
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Locale

private const val DB_TASK_GROUP = "db"
private const val ANDROID_APPLICATION_PLUGIN_ID = "com.android.application"
private const val ANDROID_BASE_PLUGIN_ID = "com.android.base"
private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

class SqliteMagicKspPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val sqlitemagic = project.extensions.create(
      "sqlitemagic",
      SqliteMagicKspPluginExtension::class.java
    )
    project.afterEvaluate {
      check(project.plugins.hasPlugin(KSP_PLUGIN_ID)) {
        "SqliteMagic KSP plugin requires '$KSP_PLUGIN_ID' in project '${project.path}'. Apply it in that module's plugins block."
      }
    }

    project.plugins.withId(KSP_PLUGIN_ID) {
      project.plugins.withId(ANDROID_BASE_PLUGIN_ID) {
        project
          .extensions
          .getByType(AndroidComponentsExtension::class.java)
          .apply {
            finalizeDsl {
              project.configureDependencies(sqlitemagic)
            }
            onVariants { variant ->
              project.configureKspVariantArgs(variant)
              if (project.plugins.hasPlugin(ANDROID_APPLICATION_PLUGIN_ID) && !variant.isDebug) {
                variant.addMigrateDbTask(project)
              }
            }
          }
      }
    }
  }
}

private fun Project.configureDependencies(sqlitemagic: SqliteMagicKspPluginExtension) {
  configureKspArgs(sqlitemagic)
  if (sqlitemagic.configureAutomatically) {
    with(dependencies) {
      add("compileOnly", "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$PLUGIN_VERSION")
      add("implementation", "com.siimkinks.sqlitemagic:sqlitemagic:$PLUGIN_VERSION")
      add("ksp", "com.siimkinks.sqlitemagic:sqlitemagic-compiler-ksp:$PLUGIN_VERSION")
    }
  }
}

private fun Project.configureKspArgs(sqlitemagic: SqliteMagicKspPluginExtension) {
  val ksp = extensions.getByType(KspExtension::class.java)
  ksp.arg("sqlitemagic.kotlin.public.extensions", sqlitemagic.publicKotlinExtensionFunctions.toString())
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

      ReleaseMigrationCoordinator.migrate(
        projectDir = projectDir,
        databaseDirectory = dbDir,
        variantName = buildTypeName
      )
    }
  }
  migrationTask.configure {
    it.group = DB_TASK_GROUP
  }
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
