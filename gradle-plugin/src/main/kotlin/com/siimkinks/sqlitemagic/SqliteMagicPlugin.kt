package com.siimkinks.sqlitemagic

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.model.ClassField
import org.gradle.api.DomainObjectSet
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

const val VERSION = "0.19.0-SNAPSHOT"
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
    project.gradle.addListener(object : DependencyResolutionListener {
      override fun beforeResolve(dependencies: ResolvableDependencies?) {
        project.gradle.removeListener(this)
        if (!sqlitemagic.configureAutomatically) {
          return
        }

        val compileDeps = project.getConfigurationDependency(depName = "implementation", fallback = "compile")
        val providedDeps = project.getConfigurationDependency(depName = "compileOnly", fallback = "provided")

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

        project.getConfigurationDependency(if (hasKpt) "kapt" else "annotationProcessor")
            .addDependency(project, "com.siimkinks.sqlitemagic:$compilerArtifact:$VERSION")
        providedDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$VERSION")
        compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic:$VERSION")
        if (sqlitemagic.useKotlin && kotlinProject) {
          compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-kotlin:$VERSION")
        }
      }

      override fun afterResolve(dependencies: ResolvableDependencies?) {
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
      it.addAptArg("sqlitemagic.project.dir", project.projectDir)
      it.addAptArg("sqlitemagic.variant.dir.name", it.dirName)
      it.addAptArg("sqlitemagic.variant.debug", it.debug)
      it.addDebugDbVersion(project)
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

  private fun <T : BaseVariant> T.addDebugDbVersion(project: Project) {
    if (debug) {
      val dbDir = File(project.projectDir, "db")
      val debugVersionFile = File(dbDir, "latest_debug.version")
      val debugDbVersion = when {
        debugVersionFile.exists() -> debugVersionFile.readLines().last().toInt()
        else -> 0
      }.inc()
      addAptArg("sqlitemagic.db.version", debugDbVersion)

      if (!dbDir.exists()) {
        dbDir.mkdirs()
      }
      debugVersionFile.writeText(debugDbVersion.toString())
    }
  }

  private fun ensureJavaVersion(javaVersion: JavaVersion) {
    if (!javaVersion.isJava7Compatible) {
      throw IllegalStateException("Source and target Java versions must be at least ${JavaVersion.VERSION_1_7}")
    }
  }
}

fun Project.getConfigurationDependency(depName: String, fallback: String = ""): DependencySet =
    try {
      configurations.getByName(depName).dependencies
    } catch (e: Exception) {
      configurations.getByName(fallback).dependencies
    }

fun DependencySet.addDependency(project: Project, dependency: String) {
  add(project.dependencies.create(dependency))
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