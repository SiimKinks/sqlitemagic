package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.manager.ReleaseMigrationCoordinator
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@CacheableTask
abstract class ExportSqliteMagicReleaseSchema : DefaultTask() {
  @get:Classpath
  abstract val classJars: ListProperty<RegularFile>

  @get:Classpath
  abstract val classDirectories: ListProperty<Directory>

  @get:Classpath
  abstract val toolingClasspath: ConfigurableFileCollection

  @get:Classpath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:Input
  abstract val databaseId: Property<String>

  @get:Input
  abstract val releaseVariant: Property<String>

  @get:Input
  abstract val generatedDatabaseClass: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @get:Inject
  abstract val execOperations: ExecOperations

  @TaskAction
  fun export() {
    val destination = outputFile.get().asFile
    execOperations
      .javaexec { spec ->
        spec.mainClass.set("com.siimkinks.sqlitemagic.migration.export.CompiledSchemaExporter")
        spec.classpath(
          classJars.get().map(RegularFile::getAsFile),
          classDirectories.get().map(Directory::getAsFile),
          bootClasspath,
          toolingClasspath
        )
        spec.args(
          generatedDatabaseClass.get(),
          databaseId.get(),
          releaseVariant.get(),
          destination.absolutePath
        )
      }
      .assertNormalExitValue()
  }
}

@CacheableTask
abstract class PrepareSqliteMagicMigrationTests : DefaultTask() {
  @get:Internal
  abstract val releaseDirectory: DirectoryProperty

  @get:Internal
  abstract val assetsDirectory: DirectoryProperty

  @get:Input
  abstract val databaseId: Property<String>

  @get:Input
  abstract val releaseVariant: Property<String>

  @get:Input
  abstract val migrationTaskPath: Property<String>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val compiledSchema: RegularFileProperty

  @get:OutputDirectory
  abstract val generatedSources: DirectoryProperty

  @get:OutputDirectory
  abstract val generatedResources: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val committedInputs: List<File>
    get() {
      val manifestFile = releaseDirectory.get().asFile.resolve("manifest.json")
      if (!manifestFile.isFile) return listOf(manifestFile)
      val manifest = readManifest(manifestFile)
      return listOf(manifestFile) + retainedResources(manifest).values
    }

  @TaskAction
  fun prepare() {
    val manifestFile = releaseDirectory.get().asFile.resolve("manifest.json")
    requirePublishedManifest(manifestFile)
    val manifest = readManifest(manifestFile)
    validateIdentity(manifest)
    val compiled = readSchema(compiledSchema.get().asFile)
    validateCurrentExport(
      manifest = manifest,
      compiled = compiled,
      migrationTaskPath = migrationTaskPath.get()
    )
    val resources = retainedResources(manifest)
    val reference = manifest.schema(manifest.currentVersion)
    val currentFile = resources.getValue(reference.name)
    check(currentFile.isFile) { missingEvidence(currentFile) }
    val currentText = readUtf8(currentFile)
    val published = try {
      MigrationMetadataJson.readStructure(
        text = currentText,
        manifest = manifest,
        reference = reference
      )
    } catch (failure: Exception) {
      throw GradleException(
        "Incomplete current release schema ${reference.version} at ${currentFile.absolutePath}. " +
            "Publish and review complete evidence via ${migrationTaskPath.get()}.",
        failure
      )
    }
    check(compiled.configuration == published.configuration && compiled.modules == published.modules) {
      "Compiled selected release configuration or module order differs from published evidence. " +
          "Run ${migrationTaskPath.get()}, review and commit the release evidence."
    }
    check(
      compiled.tableSql.toSet() == published.tableSql.toSet() &&
          compiled.indexSql.toSet() == published.indexSql.toSet()
    ) {
      "Compiled selected release tables or indices differ from the published current .struct. " +
          "Advance the database version, run ${migrationTaskPath.get()}, review and commit the release evidence."
    }
    val retainedViewNames = MigrationMetadataJson.structureViewNames(
      text = currentText,
      reference = manifest.schema(manifest.currentVersion)
    )
    check(retainedViewNames.toSet() == compiled.views.map(MigrationViewSchema::name).toSet()) {
      "Compiled selected release view names differ from the published current .struct. " +
          "Advance the database version, run ${migrationTaskPath.get()}, review and commit the release evidence."
    }
    val sourceRoot = generatedSources.get().asFile
    val resourceDirectory = generatedResources.get().asFile
    check(!sourceRoot.exists() || sourceRoot.deleteRecursively())
    check(!resourceDirectory.exists() || resourceDirectory.deleteRecursively())
    val namespace = migrationResourceRoot(
      databaseId = manifest.databaseId,
      releaseVariant = manifest.releaseVariant
    )
    val targetRoot = resourceDirectory.resolve(namespace)
    check(targetRoot.mkdirs())
    manifestFile.copyTo(targetRoot.resolve("manifest.json"))
    resources
      .filterValues(File::isFile)
      .forEach { (name, file) ->
        val destination = targetRoot.resolve(name)
        check(destination.parentFile.isDirectory || destination.parentFile.mkdirs())
        file.copyTo(destination)
      }
    compiledSchema.get().asFile.copyTo(targetRoot.resolve("current.schema.json"))
    val descriptor = sourceRoot.resolve("com/siimkinks/sqlitemagic/SqliteMagicMigrationDatabase.kt")
    check(descriptor.parentFile.mkdirs())
    descriptor.writeText(
      descriptorSource(
        manifest = manifest,
        namespace = namespace
      )
    )
  }

  private fun requirePublishedManifest(file: File) {
    check(file.isFile) {
      "Missing migration manifest ${file.absolutePath}. For an existing migration history, run " +
          "${migrationTaskPath.get().substringBeforeLast(':')}:generateSqliteMagicMigrationManifest, " +
          "review and commit the manifest, then rerun migration tests. For a new database, run " +
          "${migrationTaskPath.get()} first."
    }
  }

  private fun readManifest(file: File) = MigrationMetadataJson.readManifest(readUtf8(file))

  private fun validateIdentity(manifest: MigrationManifest) {
    check(manifest.databaseId == databaseId.get() && manifest.releaseVariant == releaseVariant.get()) {
      "Published migration history belongs to '${manifest.databaseId}/${manifest.releaseVariant}', expected " +
          "'${databaseId.get()}/${releaseVariant.get()}'."
    }
  }

  // Track missing references too, so restoring an input invalidates Gradle's cached resources.
  private fun retainedResources(manifest: MigrationManifest): Map<String, File> {
    val schemas = manifest.schemas
      .associate { resource ->
        check(resource.name.startsWith("schemas/")) {
          "Release schema resource must be under schemas/: ${resource.name}"
        }
        resource.name to releaseDirectory.get().asFile.resolve(resource.name.removePrefix("schemas/"))
      }
    val scripts = manifest.steps
      .flatMap(MigrationStep::sqlResources)
      .associate { resource -> resource.name to assetsDirectory.get().asFile.resolve(resource.name) }
    val modules = (manifest.modules + manifest.schemas
      .mapNotNull(ReleaseSchemaResource::modules)
      .flatten()).distinct()
    val prefixes = modules.map { module ->
      when (module) {
        manifest.databaseId -> ""
        else -> module
      }
    }
    val conventionalScripts = assetsDirectory.get()
      .asFile
      .listFiles().orEmpty()
      .filter(File::isFile)
      .filter { file ->
        file.extension == "sql" && prefixes.any { prefix ->
          val version = file.nameWithoutExtension.removePrefix(prefix).toIntOrNull()
          version != null && version > 0 && version <= manifest.currentVersion &&
              file.name == "$prefix$version.sql"
        }
      }
      .associateBy(File::getName)
    return schemas + conventionalScripts + scripts
  }

  private fun missingEvidence(file: File) = "Missing required release input ${file.absolutePath}. Run " +
      "${migrationTaskPath.get()}, review and commit the listed resources; preparation does not repair history."
}

@DisableCachingByDefault(because = "Explicitly publishes reviewed release evidence into consumer source directories")
abstract class PublishSqliteMagicRelease : DefaultTask() {
  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  @get:Internal
  abstract val databaseDirectory: DirectoryProperty

  @get:Input
  abstract val releaseVariant: Property<String>

  @get:Input
  abstract val assetsBuildType: Property<String>

  @get:Internal
  abstract val assetsDirectory: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val selectedRelease: Property<String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val currentStructures: ConfigurableFileCollection

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  abstract val compiledSchema: RegularFileProperty

  @TaskAction
  fun publish() {
    check(selectedRelease.orNull == null || selectedRelease.get() == releaseVariant.get()) {
      "Only selected migration release '${selectedRelease.get()}' may publish authoritative history"
    }
    val captured = compiledSchema.orNull?.asFile?.let(::readSchema)
    check(captured == null || captured.releaseVariant == releaseVariant.get()) {
      "Compiled schema belongs to a different selected release variant"
    }
    val missingStructures = currentStructures.files.filterNot(File::isFile)
    check(missingStructures.isEmpty()) {
      "Missing generated SqliteMagic structure(s): ${missingStructures.joinToString()}. " +
          "Run ${path.substringBeforeLast(':')}:cleanSqliteMagicMetadata, then rerun $path " +
          "to rebuild KSP metadata for the application and configured schema submodules."
    }
    ReleaseMigrationCoordinator.migrate(
      projectDir = projectDirectory.get().asFile,
      databaseDirectory = databaseDirectory.get().asFile,
      variantName = assetsBuildType.get(),
      currentStructureFiles = currentStructures.files.sortedBy(File::getName),
      compiledRelease = captured,
      evidenceAssetsDirectory = assetsDirectory.orNull?.asFile
        ?: projectDirectory.get().asFile.resolve("src/${assetsBuildType.get()}/assets")
    )
  }
}

@DisableCachingByDefault(because = "Publishes mutable current compiler structures shared by the configured modules")
abstract class PublishSqliteMagicStructures : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val stagedDirectories: ConfigurableFileCollection

  @get:Internal
  abstract val destinationDirectory: DirectoryProperty

  @TaskAction
  fun publish() = publishStagedStructures(
    stagedDirectories = stagedDirectories.files.toList(),
    destination = destinationDirectory.get().asFile
  )
}

internal fun migrationResourceRoot(
  databaseId: String,
  releaseVariant: String
): String {
  fun encode(value: String) = value.toByteArray(StandardCharsets.UTF_8)
    .joinToString(
      separator = "",
      transform = ::encodedByte
    )
  return "sqlitemagic/migrations/${encode(databaseId)}/${encode(releaseVariant)}/"
}

private fun readUtf8(file: File) = MigrationScriptValidation.readUtf8(file.inputStream())
private fun readSchema(file: File): ReleaseSchemaSnapshot = MigrationMetadataJson.readSchema(readUtf8(file))

private fun kotlinLiteral(value: String) = buildString {
  append('"')
  value.forEach { character ->
    when (character) {
      '\\' -> append("\\\\")
      '"' -> append("\\\"")
      '$' -> append("\\$")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> append(character)
    }
  }
  append('"')
}

private fun descriptorSource(
  manifest: MigrationManifest,
  namespace: String
) = $$"""
package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.migration.MigrationDatabaseDescriptor
import java.io.FileNotFoundException
import java.io.InputStream

object SqliteMagicMigrationDatabase : MigrationDatabaseDescriptor {
  override val databaseId = $${kotlinLiteral(manifest.databaseId)}
  override val releaseVariant = $${kotlinLiteral(manifest.releaseVariant)}
  override val currentVersion = $${manifest.currentVersion}
  override val resourceRoot = $${kotlinLiteral(namespace)}
  override val manifestResource = "manifest.json"
  override val currentSchemaResource = "current.schema.json"

  override fun openResource(name: String): InputStream =
    javaClass.classLoader?.getResourceAsStream(resourceRoot + name)
      ?: throw FileNotFoundException("Missing migration test resource: $resourceRoot$name")
}
""".trimIndent() + "\n"

private fun validateCurrentExport(
  manifest: MigrationManifest,
  compiled: ReleaseSchemaSnapshot,
  migrationTaskPath: String
) {
  check(compiled.version == manifest.currentVersion) {
    "Compiled selected release version ${compiled.version} differs from published ${manifest.currentVersion}. " +
        "Run $migrationTaskPath, review and commit the release evidence."
  }
  if (compiled.databaseId != manifest.databaseId || compiled.releaseVariant != manifest.releaseVariant ||
    !manifest.modules.containsAll(compiled.modules)
  ) {
    throw GradleException(
      "Compiled selected release identity differs from published evidence. " +
          "Run $migrationTaskPath, review and commit the release evidence."
    )
  }
}

private fun encodedByte(byte: Byte) = "%" + (byte.toInt() and 0xff)
  .toString(16)
  .padStart(
    length = 2,
    padChar = '0'
  )
