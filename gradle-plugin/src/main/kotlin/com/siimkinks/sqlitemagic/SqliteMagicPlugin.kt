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

const val VERSION = "0.12.0"

class SqliteMagicPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val sqlitemagic = project.extensions.create("sqlitemagic", SqliteMagicPluginExtension::class.java)
    configureProject(project, sqlitemagic)

    project.plugins.all {
      when (it) {
        is AppPlugin -> {
          val androidExtension = project.extensions.getByType(AppExtension::class.java)
          configureAndroid(project, sqlitemagic, androidExtension, androidExtension.applicationVariants)
        }
        is LibraryPlugin -> {
          val androidExtension = project.extensions.getByType(LibraryExtension::class.java)
          configureAndroid(project, sqlitemagic, androidExtension, androidExtension.libraryVariants)
        }
      }
    }
  }

  private fun configureProject(project: Project, sqlitemagic: SqliteMagicPluginExtension) {
    val compileDeps = project.getConfigurationDependency("compile")
    val providedDeps = project.getConfigurationDependency("provided")
    val aptDeps = project.getConfigurationDependency("annotationProcessor")

    project.gradle.addListener(object : DependencyResolutionListener {
      override fun beforeResolve(dependencies: ResolvableDependencies?) {
        val kotlinProject = listOf("kotlin-android", "kotlin", "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm")
            .asSequence()
            .mapNotNull { project.plugins.findPlugin(it) }
            .firstOrNull() != null

        val compilerArtifact = when {
          !sqlitemagic.generateMagicMethods -> "sqlitemagic-compiler"
          kotlinProject -> "sqlitemagic-compiler-kotlin"
          else -> "sqlitemagic-compiler-magic"
        }

        if (kotlinProject) {
          val hasKpt = project.plugins.findPlugin("kotlin-kapt") != null || project.plugins.findPlugin("org.jetbrains.kotlin.kapt") != null
          if (!hasKpt) {
            throw IllegalStateException("Missing kotlin kapt plugin!")
          }
          project.getConfigurationDependency("kapt")
        } else {
          aptDeps
        }.addDependency(project, "com.siimkinks.sqlitemagic:$compilerArtifact:$VERSION")

        providedDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic-annotations:$VERSION")
        compileDeps.addDependency(project, "com.siimkinks.sqlitemagic:sqlitemagic:$VERSION")

        project.gradle.removeListener(this)
      }

      override fun afterResolve(dependencies: ResolvableDependencies?) {
      }
    })

    project.afterEvaluate {
      System.setProperty("SQLITE_MAGIC_GENERATE_LOGGING", sqlitemagic.generateLogging.toString())
      System.setProperty("SQLITE_MAGIC_AUTO_LIB", sqlitemagic.autoValueAnnotation)
      System.setProperty("SQLITE_MAGIC_K_PUBLIC_EXTENSIONS", sqlitemagic.publicKotlinExtensionFunctions.toString())
      System.setProperty("PROJECT_DIR", project.projectDir.toString())
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

    val transform: SqliteMagicTransform?
    if (androidExtension is AppExtension) {
      transform = SqliteMagicTransform(project, sqlitemagic, androidExtension)
      androidExtension.registerTransform(transform)
    } else {
      transform = null
    }

    variants.all {
      transform?.putJavaCompileTask(it)
      addConfigVariantDbTask(project, it)
      addMigrationTask(project, it)
    }
  }

  private fun addConfigVariantDbTask(project: Project, variant: BaseVariant) {
    val configTask = project.task("config${variant.name.capitalize()}Db").doFirst {
      var dbVersion = "1"
      var dbName = "\"database.db\""
      val variantData = variant.javaClass.getMethod("getVariantData").invoke(variant) as BaseVariantData<*>
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
      System.setProperty("SQLITE_MAGIC_DB_VERSION", dbVersion)
      System.setProperty("SQLITE_MAGIC_DB_NAME", dbName)
    }
    variant.javaCompiler.dependsOn(configTask)
  }

  private fun addMigrationTask(project: Project, variant: BaseVariant) {
    // TODO implement
  }

  private fun ensureJavaVersion(javaVersion: JavaVersion) {
    if (!javaVersion.isJava7Compatible) {
      throw IllegalStateException("Source and target Java versions must be at least ${JavaVersion.VERSION_1_7}")
    }
  }
}

fun Project.getConfigurationDependency(depName: String): DependencySet {
  return configurations.getByName(depName).dependencies
}

fun DependencySet.addDependency(project: Project, dependency: String) {
  add(project.dependencies.create(dependency))
}