package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@DisableCachingByDefault(because = "Explicitly adopts retained release history without rewriting migrations")
abstract class GenerateSqliteMagicMigrationManifest : DefaultTask() {
  @get:Internal
  abstract val releaseDirectory: DirectoryProperty

  @get:Internal
  abstract val assetsDirectory: DirectoryProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val compiledSchema: RegularFileProperty

  @get:Input
  @get:Optional
  abstract val firstVersion: Property<Int>

  @TaskAction
  fun generate() {
    val releaseRoot = releaseDirectory.get().asFile
    val manifestFile = releaseRoot.resolve("manifest.json")
    val compiledFile = compiledSchema.get().asFile
    val compiled = MigrationMetadataJson.readSchema(compiledFile.readText())
    if (manifestFile.isFile) {
      val existing = MigrationMetadataJson.readManifest(manifestFile.readText())
      check(existing.databaseId == compiled.databaseId && existing.releaseVariant == compiled.releaseVariant) {
        "Existing migration manifest belongs to a different database or selected release variant: $manifestFile"
      }
      logger.lifecycle("SqliteMagic migration manifest already exists: $manifestFile")
      return
    }
    check(!manifestFile.exists()) { "Migration manifest is not a regular file: $manifestFile" }
    val structures = releaseRoot
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.extension == "struct" && it.nameWithoutExtension.toIntOrNull() != null }
      .sortedBy { it.nameWithoutExtension.toInt() }
    check(structures.isNotEmpty()) {
      "No retained release schemas in $releaseRoot. Restore db/releases/N.struct from the old project or " +
          "version control before generating the manifest. SQL scripts alone cannot establish a schema baseline. " +
          "For a new database, run the selected migrate<Variant>Db task first."
    }
    val currentStructure = structures.last()
    val currentVersion = currentStructure.nameWithoutExtension.toInt()
    check(compiled.version >= currentVersion) {
      "Compiled database version ${compiled.version} precedes retained release $currentVersion. " +
          "Select the correct release variant and database version."
    }
    val references = structures.map { file ->
      val version = file.nameWithoutExtension.toInt()
      try {
        ReleaseSchemaResource(
          version = version,
          name = "schemas/${file.name}",
          configuration = compiled.configuration.takeIf { version == compiled.version },
          modules = compiled.modules.takeIf { version == compiled.version },
          viewNames = compiled.views
            .map(MigrationViewSchema::name)
            .takeIf {
              version == compiled.version && !MigrationMetadataJson.structureHasViewMetadata(file.readText())
            }
        )
      } catch (failure: Exception) {
        throw GradleException("Cannot read retained release schema $file. Restore complete schema metadata.", failure)
      }
    }
    val baseline = firstVersion.orNull ?: references.first().version
    val assetsRoot = assetsDirectory.get().asFile
    val assetFiles = assetsRoot.listFiles()
      .orEmpty()
      .filter { it.isFile && it.extension == "sql" }
    assetFiles.forEach { file ->
      val matches = compiled.modules.count { module ->
        val prefix = when (module) {
          compiled.databaseId -> ""
          else -> module
        }
        val version = file.nameWithoutExtension.removePrefix(prefix)
          .toIntOrNull()
        version != null && version > 0 && file.name == "$prefix$version.sql"
      }
      check(matches <= 1) { "Ambiguous migration asset $file; review module identities before adoption" }
      check(matches == 1 || file.nameWithoutExtension.lastOrNull()?.isDigit() != true) {
        "Unrecognized migration asset $file. Restore the corresponding schema submodule in the selected release " +
            "or review historical module identities and author the manifest manually."
      }
    }
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = compiled.databaseId,
      releaseVariant = compiled.releaseVariant,
      currentVersion = currentVersion,
      modules = compiled.modules,
      schemas = references,
      steps = (1..currentVersion).map { version ->
        MigrationStep(
          toVersion = version,
          sqlResources = compiled.modules.mapNotNull { module ->
            val prefix = when (module) {
              compiled.databaseId -> ""
              else -> module
            }
            val file = assetsRoot.resolve("$prefix$version.sql")
            file.takeIf(File::isFile)
              ?.let { script ->
                migrationResource(
                  file = script,
                  validate = version > baseline
                )
              }
          }
        )
      }
    )
    manifest.validate()
    manifest.schema(baseline)
    references.forEach { reference ->
      if (reference.version < baseline) return@forEach
      val file = releaseRoot.resolve(reference.name.removePrefix("schemas/"))
      try {
        val structureText = file.readText()
        val snapshot = MigrationMetadataJson.readStructure(
          text = structureText,
          manifest = manifest,
          reference = reference
        )
        if (reference.version == compiled.version) {
          check(
            snapshot.tableSql.toSet() == compiled.tableSql.toSet() &&
                snapshot.indexSql.toSet() == compiled.indexSql.toSet()
          ) {
            "Compiled schema differs from retained release ${reference.version}. " +
                "Advance the database version and generate the manifest again before creating the next migration."
          }
          if (reference.viewNames == null) {
            val retainedViewNames = MigrationMetadataJson.structureViewNames(structureText)
              .toSet()
            val compiledViewNames = compiled.views.map(MigrationViewSchema::name)
              .toSet()
            check(retainedViewNames == compiledViewNames) {
              "Compiled view names differ from retained release ${reference.version}. Advance the database version."
            }
          }
        }
      } catch (failure: Exception) {
        throw GradleException(
          "Cannot adopt release schema ${reference.version} at $file: ${failure.message}. " +
              "Restore complete retained evidence, or set migrationTesting.firstVersion to a complete baseline.",
          failure
        )
      }
    }
    val text = MigrationMetadataJson.writeManifest(manifest)
    val temporary = File.createTempFile("manifest-", ".tmp", releaseRoot)
    try {
      temporary.writeText(text)
      Files.move(temporary.toPath(), manifestFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
      temporary.delete()
    }
    logger.lifecycle("Created $manifestFile from ${references.size} retained schemas through version $currentVersion.")
    logger.lifecycle("Review and commit the manifest, then run release migration publication or migration tests.")
  }

  private fun migrationResource(
    file: File,
    validate: Boolean
  ): MigrationResource {
    val text = MigrationScriptValidation.readUtf8(file.inputStream())
    if (validate) {
      MigrationScriptValidation.validate(
        text = text,
        resourceName = file.absolutePath
      )
    }
    return MigrationResource(
      name = file.name,
      required = true,
      allowEmpty = text.lineSequence()
        .all { line ->
          line.isBlank() || line.trimStart()
            .startsWith("--")
        }
    )
  }
}

@DisableCachingByDefault(because = "Removes regenerable legacy and current compiler metadata and KSP caches")
abstract class CleanSqliteMagicMetadata : DefaultTask() {
  @get:Internal
  abstract val databaseDirectory: DirectoryProperty

  @get:Internal
  abstract val buildDirectory: DirectoryProperty

  @TaskAction
  fun clean() {
    val databaseRoot = databaseDirectory.get().asFile
    databaseRoot.listFiles()
      .orEmpty()
      .filter { file ->
        file.isFile && (file.name == "latest.struct" ||
            file.name.startsWith("latest_") && file.extension in setOf("struct", "version") ||
            file.extension == "changed")
      }
      .forEach(::deleteMetadata)
    val buildRoot = buildDirectory.get().asFile
    listOf("sqlitemagic", "kspCaches", "generated/ksp")
      .map(buildRoot::resolve)
      .forEach(::deleteMetadata)
  }

  private fun deleteMetadata(file: File) {
    check(!file.exists() || file.deleteRecursively()) { "Failed to remove generated metadata: $file" }
  }
}
