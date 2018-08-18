package com.siimkinks.sqlitemagic

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.model.ClassField
import com.siimkinks.sqlitemagic.structure.MigrationsHandler
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

const val VERSION = "0.23.2-SNAPSHOT"
const val DB_TASK_GROUP = "db"

class SqliteMagicPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val sqlitemagic = project.extensions.create("sqlitemagic", SqliteMagicPluginExtension::class.java)
    configureProject(project, sqlitemagic)

    project.plugins.all {
      when (it) {
        is AppPlugin -> {
          val androidExtension = project.extensions.getByType(AppExtension::class.java)
          val variants = androidExtension.applicationVariants
          configureAptArgs(project, sqlitemagic, variants)
          configureAndroid(project, sqlitemagic, androidExtension, variants)
        }
        is LibraryPlugin -> {
          val androidExtension = project.extensions.getByType(LibraryExtension::class.java)
          val variants = androidExtension.libraryVariants
          configureAptArgs(project, sqlitemagic, variants)
          configureAndroid(project, sqlitemagic, androidExtension, variants)
        }
      }
    }
  }

  private fun configureProject(project: Project, sqlitemagic: SqliteMagicPluginExtension) {
    project.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
      override fun beforeEvaluate(project: Project) {}

      override fun afterEvaluate(project: Project, state: ProjectState?) {
        project.gradle.removeProjectEvaluationListener(this)
        if (!sqlitemagic.configureAutomatically) {
          return
        }

        val compileDeps = project.getConfiguration(depName = "implementation", fallback = "compile")
        val providedDeps = project.getConfiguration(depName = "compileOnly", fallback = "provided")

        val kotlinProject = project.hasAnyPlugin("kotlin-android", "kotlin", "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm")

        val compilerArtifact = when {
          !sqlitemagic.generateMagicMethods -> "sqlitemagic-compiler"
          sqlitemagic.useKotlin && kotlinProject -> "sqlitemagic-compiler-kotlin"
          else -> "sqlitemagic-compiler-magic"
        }

        val hasKpt = project.hasAnyPlugin("kotlin-kapt", "org.jetbrains.kotlin.kapt")
        if (sqlitemagic.useKotlin && kotlinProject && !hasKpt) {
          throw IllegalStateException("Missing kotlin kapt plugin! Set sqlitemagic.useKotlin=false or add kotlin-kapt plugin")
        }

        project.getConfiguration(if (hasKpt) "kapt" else "annotationProcessor")
            .addDependency(project, "com.siimkinks.sqlitemagic:$compilerArtifact:$VERSION")
        providedDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$VERSION")
        compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic:$VERSION")
        if (sqlitemagic.useKotlin && kotlinProject) {
          compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-kotlin:$VERSION")
        }
      }
    })
  }

  private fun <T : BaseVariant> configureAptArgs(project: Project,
                                                 sqlitemagic: SqliteMagicPluginExtension,
                                                 variants: DomainObjectSet<T>) {
    variants.all {
      it.addAptArg("sqlitemagic.generate.logging", sqlitemagic.generateLogging)
      it.addAptArg("sqlitemagic.auto.lib", sqlitemagic.autoValueAnnotation)
      it.addAptArg("sqlitemagic.kotlin.public.extensions", sqlitemagic.publicKotlinExtensionFunctions)
      it.addAptArg("sqlitemagic.migrate.debug", sqlitemagic.migrateDebugDatabase)
      it.addAptArg("sqlitemagic.project.dir", project.projectDir)
      it.addAptArg("sqlitemagic.variant.name", it.name)
      it.addAptArg("sqlitemagic.variant.debug", it.debug)
      sqlitemagic.mainModulePath?.let { mainModulePath ->
        it.addAptArg("sqlitemagic.main.module.path", File(mainModulePath).absolutePath)
      }
      it.addDebugDbVersion(project, sqlitemagic)
    }
  }

  private fun <T : BaseVariant> configureAndroid(project: Project,
                                                 sqlitemagic: SqliteMagicPluginExtension,
                                                 androidExtension: BaseExtension,
                                                 variants: DomainObjectSet<T>) {
    project.afterEvaluate {
      ensureJavaVersion(androidExtension.compileOptions.sourceCompatibility)
      ensureJavaVersion(androidExtension.compileOptions.targetCompatibility)
    }

    if (androidExtension is AppExtension) {
      val transform = SqliteMagicTransform(project, sqlitemagic)
      androidExtension.registerTransform(transform)
      variants.all {
        it.configureVariant(transform, project)
      }
      androidExtension.testVariants.all {
        it.configureVariant(transform, project)
      }
    }
  }

  private fun <T : BaseVariant> T.configureVariant(transform: SqliteMagicTransform, project: Project) {
    transform.putJavaCompileTask(this)
    addConfigVariantDbTask(project, this)
    if (!debug) {
      addMigrateVariantDbTask(project)
    }
  }

  // FIXME remove?
  private fun addConfigVariantDbTask(project: Project, variant: BaseVariant) {
    val configTask = project.task("config${variant.name.capitalize()}Db").doFirst {
      var dbVersion = "1"
      var dbName = "\"database.db\""
      val variantData = variant.javaClass.getMethod("getVariantData").invoke(variant) as BaseVariantData
      variantData.variantConfiguration.buildConfigItems.forEach {
        if (it is ClassField) {
          var gotValue = false
          if ("DB_VERSION".equals(it.name, ignoreCase = true)) {
            dbVersion = it.value
            gotValue = true
          }
          if ("DB_NAME".equals(it.name, ignoreCase = true)) {
            dbName = it.value
            gotValue = true
          }
          if (gotValue) {
            return@forEach
          }
        }
      }
      variant.addAptArg("sqlitemagic.db.name", dbName)
      if (!variant.debug) {
        variant.addAptArg("sqlitemagic.db.version", dbVersion)
      }
    }
    configTask.group = DB_TASK_GROUP
    variant.javaCompiler.dependsOn(configTask)
  }

  private fun BaseVariant.addMigrateVariantDbTask(project: Project) {
    val migrationTask = project.task("migrate${name.capitalize()}Db").doFirst {
      val projectDir = project.projectDir
      val dbDir = File(projectDir, "db")
      check(dbDir.exists()) { "Database metadata directory must exist in order to create migrations. Build project and try again…" }

      MigrationsHandler.handleReleaseMigrations(
          projectDir = projectDir,
          dbDir = dbDir,
          variantName = name)
    }
    migrationTask.group = DB_TASK_GROUP
  }

  private fun <T : BaseVariant> T.addDebugDbVersion(project: Project, sqlitemagic: SqliteMagicPluginExtension) {
    if (debug) {
      val mainModulePath = sqlitemagic.mainModulePath
      val baseDir = mainModulePath?.let(::File) ?: project.projectDir
      val dbDir = File(baseDir, "db")
      val debugVersionFile = File(dbDir, "latest_$name.version")
      val debugDbVersion = when {
        debugVersionFile.exists() -> debugVersionFile.readLines().last().toInt()
        else -> 1000
      }.run { if (mainModulePath == null) inc() else this }
      addAptArg("sqlitemagic.db.version", debugDbVersion)

      if (mainModulePath == null) {
        if (!dbDir.exists()) {
          dbDir.mkdirs()
        }
        debugVersionFile.writeText(debugDbVersion.toString())
      }
    }
  }

  private fun ensureJavaVersion(javaVersion: JavaVersion) {
    if (!javaVersion.isJava7Compatible) {
      throw IllegalStateException("Source and target Java versions must be at least ${JavaVersion.VERSION_1_7}")
    }
  }
}

fun Project.getConfiguration(depName: String, fallback: String = ""): Configuration =
    try {
      configurations.getByName(depName)
    } catch (e: Exception) {
      configurations.getByName(fallback)
    }

fun Configuration.addDependency(project: Project, dependency: String) {
  dependencies.add(project.dependencies.create(dependency))
}

fun Project.hasAnyPlugin(vararg pluginIds: String): Boolean = pluginIds
    .asSequence()
    .mapNotNull(this.plugins::findPlugin)
    .firstOrNull() != null

private fun BaseVariant.addAptArg(name: String, value: Any) {
  javaCompileOptions.annotationProcessorOptions.arguments[name] = value.toString()
  javaCompile().options.compilerArgs.add("-A$name=$value")
}

fun BaseVariant.javaCompile(): JavaCompile {
  val javaCompiler = javaCompiler
  return javaCompiler as? JavaCompile ?: javaCompile
}

val BaseVariant.debug: Boolean
  get() = buildType.name == "debug"