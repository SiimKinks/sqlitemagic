package com.siimkinks.sqlitemagic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspExtension
import com.siimkinks.sqlitemagic.manager.DatabaseStructurePublication
import com.siimkinks.sqlitemagic.manager.ReleaseMigrationCoordinator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.Locale

private const val DB_TASK_GROUP = "db"
private const val ANDROID_APPLICATION_PLUGIN_ID = "com.android.application"
private const val ANDROID_BASE_PLUGIN_ID = "com.android.base"
private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
private const val OPTION_STRUCTURE_INPUT_DIRS = "sqlitemagic.structure.input.dirs"
private const val OPTION_STRUCTURE_OUTPUT_DIR = "sqlitemagic.structure.output.dir"
private const val STRUCTURE_PUBLICATION_LOCK = "sqlitemagicStructurePublicationLock"

class SqliteMagicKspPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val variantNames = linkedSetOf<String>()
    val sqlitemagic = project.extensions.create(
      "sqlitemagic",
      SqliteMagicKspPluginExtension::class.java
    )
    project.afterEvaluate {
      check(project.plugins.hasPlugin(KSP_PLUGIN_ID)) {
        "SqliteMagic KSP plugin requires '$KSP_PLUGIN_ID' in project '${project.path}'. Apply it in that module's plugins block."
      }
    }
    configureStructurePropagation(
      currentProject = project,
      variantNames = variantNames
    )

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
              variantNames += variant.name
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

private fun configureStructurePropagation(
  currentProject: Project,
  variantNames: Set<String>
) {
  currentProject.gradle.projectsEvaluated {
    val submoduleProjects = currentProject.structureSubmoduleProjects()
    if (submoduleProjects.isEmpty()) return@projectsEvaluated
    val publicationLock = currentProject.gradle.sharedServices.registerIfAbsent(
      STRUCTURE_PUBLICATION_LOCK,
      StructurePublicationLock::class.java
    ) {
      it.maxParallelUsages.set(1)
    }
    variantNames.sorted().forEach { variantName ->
      val variantTaskName = variantName.capitalize()
      val consumerTaskName = "ksp${variantTaskName}Kotlin"
      val databaseDirectory = currentProject.projectDir.resolve("db")
      val structureChangeMarker = databaseDirectory.resolve("submodules.changed")
      val consumerTask = currentProject.tasks.findByName(consumerTaskName)
        ?: throw GradleException(
          "SqliteMagic KSP task '$consumerTaskName' was not found for variant '$variantName' " +
              "in project '${currentProject.path}'"
        )
      val (producerTasks, stagedDirectories) = submoduleProjects
        .map { submoduleProject ->
          val producerTask = submoduleProject.tasks.findByName(consumerTaskName)
            ?: throw GradleException(
              "SqliteMagic KSP cannot consume submodule '${submoduleProject.path}' for variant " +
                  "'$variantName': matching task '$consumerTaskName' was not found"
            )
          producerTask to submoduleProject.structureStagingDirectory(variantName)
        }
        .unzip()
      val publicationTask = currentProject.tasks.register(
        "publishSqliteMagic${variantTaskName}Structures",
        DefaultTask::class.java
      ) { task ->
        task.doNotTrackState("Publishes current SqliteMagic structures into a shared database metadata directory")
        task.usesService(publicationLock)
        task.dependsOn(producerTasks)
        task.doLast {
          publishStagedStructures(
            stagedDirectories = stagedDirectories,
            destination = databaseDirectory
          )
        }
      }
      consumerTask.apply {
        dependsOn(publicationTask)
        inputs
          .files(currentProject.files(stagedDirectories).builtBy(producerTasks))
          .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs
          .files(currentProject.files(structureChangeMarker))
          .withPropertyName("sqlitemagicSubmoduleStructureChange")
          .withPathSensitivity(PathSensitivity.RELATIVE)
      }
      val structureInputDirectories = stagedDirectories.joinToString(
        separator = File.pathSeparator,
        transform = File::getAbsolutePath
      )
      val kspTask = consumerTask as KspAATask
      kspTask.kspConfig.apOptions.put(
        OPTION_STRUCTURE_INPUT_DIRS,
        structureInputDirectories
      )
      kspTask.kspConfig.processorOptions.put(
        OPTION_STRUCTURE_INPUT_DIRS,
        structureInputDirectories
      )
    }
  }
}

private fun publishStagedStructures(
  stagedDirectories: List<File>,
  destination: File
) {
  val stagedFilesByName = stagedDirectories
    .flatMap(::structureFiles)
    .groupBy(File::getName)
  val duplicateNames = stagedFilesByName.filterValues { it.size > 1 }.keys
  check(duplicateNames.isEmpty()) {
    "Multiple configured SqliteMagic submodules produced the same current structure file(s): " +
        duplicateNames.sorted().joinToString()
  }
  val stagedFiles = stagedFilesByName.mapValues { (_, files) -> files.single() }
  val publishedFiles = structureFiles(destination).associateBy(File::getName)
  val changedPublishedStructure = stagedFiles.keys
    .intersect(publishedFiles.keys)
    .any { name ->
      DatabaseStructurePublication.hasPersistentChanges(
        previousFile = publishedFiles.getValue(name),
        currentFile = stagedFiles.getValue(name)
      )
    }
  val addedPersistentStructure = stagedFiles
    .filterKeys { it !in publishedFiles }
    .values
    .any(DatabaseStructurePublication::hasPersistentObjects)
  val removedPersistentStructure = publishedFiles
    .filterKeys { it !in stagedFiles }
    .values
    .any(DatabaseStructurePublication::hasPersistentObjects)
  val structuresChanged = changedPublishedStructure || addedPersistentStructure || removedPersistentStructure
  val hasMainStructureBaseline = destination.resolve("latest.struct").isFile

  check(destination.isDirectory || destination.mkdirs()) {
    "Failed to create SqliteMagic database metadata directory ${destination.absolutePath}"
  }
  publishedFiles.values.forEach { publishedFile ->
    check(publishedFile.delete()) {
      "Failed to remove stale SqliteMagic structure ${publishedFile.absolutePath}"
    }
  }
  stagedFiles.forEach { (name, stagedFile) ->
    stagedFile.copyTo(
      target = destination.resolve(name),
      overwrite = true
    )
  }
  if (structuresChanged && hasMainStructureBaseline) {
    val changeMarker = destination.resolve("submodules.changed")
    check(changeMarker.createNewFile() || changeMarker.isFile) {
      "Failed to record changed SqliteMagic submodule structures in ${destination.absolutePath}"
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
      if (sqlitemagicMainModulePath != null) {
        val structureOutputDirectory = structureStagingDirectory(variant.name)
        task.kspConfig.apOptions.put(
          OPTION_STRUCTURE_OUTPUT_DIR,
          structureOutputDirectory.absolutePath
        )
        task.kspConfig.processorOptions.put(
          OPTION_STRUCTURE_OUTPUT_DIR,
          structureOutputDirectory.absolutePath
        )
        task.outputs.dir(structureOutputDirectory)
      }
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

private fun Project.structureSubmoduleProjects() = projectDir.canonicalFile
  .let { canonicalProjectDir ->
    configurations
      .asSequence()
      .filter { configuration ->
        configuration.name == "implementation" || configuration.name.endsWith("Implementation")
      }
      .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
      .map { rootProject.project(it.path) }
      .distinct()
      .filter { dependencyProject ->
        dependencyProject
          .sqlitemagicMainModulePath
          ?.let { mainModulePath ->
            File(dependencyProject.rootDir, mainModulePath).canonicalFile == canonicalProjectDir
          }
          ?: false
      }
      .toList()
  }

private fun Project.structureStagingDirectory(variantName: String) =
  layout.buildDirectory
    .dir("sqlitemagic/${variantName.lowerFirst()}/structures")
    .get()
    .asFile

private fun structureFiles(directory: File) = directory
  .listFiles()
  .orEmpty()
  .filter { file ->
    file.isFile && file.name.startsWith("latest_") && file.name.endsWith(".struct")
  }

private fun Variant.kspTaskName(): String = "ksp${name.capitalize()}Kotlin"

private fun String.capitalize() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.getDefault())
    else -> it.toString()
  }
}

private fun String.lowerFirst() = replaceFirstChar { character ->
  character.lowercase(Locale.ROOT)
}

private val Project.sqlitemagicMainModulePath
  get() = extensions
    .findByType(SqliteMagicKspPluginExtension::class.java)
    ?.mainModulePath

private val ComponentIdentity.isDebug: Boolean
  get() = buildType == "debug"

private abstract class StructurePublicationLock : BuildService<BuildServiceParameters.None>
