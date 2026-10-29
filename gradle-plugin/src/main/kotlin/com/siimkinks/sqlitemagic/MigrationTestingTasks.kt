package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.manager.ReleaseMigrationCoordinator
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.gradle.api.DefaultTask
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
    get() = listOf(releaseDirectory.get().asFile.resolve("manifest.json")) + retainedResources().values

  @TaskAction
  fun prepare() {
    // Release evidence may be incomplete while application unit tests run. Validate it only in the migration helper.
    val compiled = readSchema(compiledSchema.get().asFile)
    val sourceRoot = generatedSources.get().asFile
    val resourceDirectory = generatedResources.get().asFile
    check(!sourceRoot.exists() || sourceRoot.deleteRecursively())
    check(!resourceDirectory.exists() || resourceDirectory.deleteRecursively())
    val namespace = migrationResourceRoot(
      databaseId = databaseId.get(),
      releaseVariant = releaseVariant.get()
    )
    val targetRoot = resourceDirectory.resolve(namespace)
    check(targetRoot.mkdirs())
    val manifestFile = releaseDirectory.get().asFile.resolve("manifest.json")
    if (manifestFile.isFile) manifestFile.copyTo(targetRoot.resolve("manifest.json"))
    retainedResources().forEach { (name, file) ->
      val destination = targetRoot.resolve(name)
      check(destination.parentFile.isDirectory || destination.parentFile.mkdirs())
      file.copyTo(destination)
    }
    compiledSchema.get().asFile.copyTo(targetRoot.resolve("current.schema.json"))
    val descriptor = sourceRoot.resolve("com/siimkinks/sqlitemagic/SqliteMagicMigrationDatabase.kt")
    check(descriptor.parentFile.mkdirs())
    descriptor.writeText(
      descriptorSource(
        databaseId = databaseId.get(),
        releaseVariant = releaseVariant.get(),
        currentVersion = compiled.version,
        namespace = namespace,
        migrationTaskPath = migrationTaskPath.get()
      )
    )
  }

  // Inventory directories without parsing the manifest, so malformed evidence cannot block unrelated test compilation.
  // Added and restored files change this input collection and invalidate cached resources.
  private fun retainedResources(): Map<String, File> {
    val releases = releaseDirectory.get().asFile
    val assets = assetsDirectory.get().asFile
    val schemas = releases
      .walkTopDown()
      .filter { it.isFile && it.extension == "struct" }
      .associateBy { "schemas/${it.relativeTo(releases).invariantSeparatorsPath}" }
    val scripts = assets.walkTopDown()
      .filter { it.isFile && it.extension == "sql" }
      .associateBy { it.relativeTo(assets).invariantSeparatorsPath }
    return schemas + scripts
  }
}

@DisableCachingByDefault(because = "Reads mutable release evidence and source assets")
abstract class SqliteMagicReleaseTask : DefaultTask() {
  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  @get:Internal
  abstract val databaseDirectory: DirectoryProperty

  @get:Input
  abstract val releaseVariant: Property<String>

  @get:Input
  @get:Optional
  abstract val selectedRelease: Property<String>

  @get:Input
  abstract val assetsBuildType: Property<String>

  @get:Internal
  abstract val assetsDirectory: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val currentStructures: ConfigurableFileCollection

  @get:Internal
  abstract val databaseVersionFile: RegularFileProperty

  protected fun checkSelectedRelease(action: String) = check(
    selectedRelease.orNull == null || selectedRelease.get() == releaseVariant.get()
  ) { "Only selected migration release '${selectedRelease.get()}' may $action" }

  protected fun checkGeneratedMetadata() {
    val missingFiles = (currentStructures.files + databaseVersionFile.get().asFile).filterNot(File::isFile)
    check(missingFiles.isEmpty()) {
      "Missing generated SqliteMagic metadata: ${missingFiles.joinToString()}. " +
          "Run ${path.substringBeforeLast(':')}:cleanSqliteMagicMetadata, then rerun $path " +
          "to rebuild KSP metadata for the application and configured schema submodules."
    }
  }

  protected fun databaseVersion() = databaseVersionFile
    .get()
    .asFile
    .readText()
    .toInt()

  protected fun currentStructureFiles() = currentStructures.files.sortedBy(File::getName)

  protected fun evidenceAssetsDirectory() = assetsDirectory.orNull?.asFile
    ?: projectDirectory
      .get()
      .asFile
      .resolve("src/${assetsBuildType.get()}/assets")
}

@DisableCachingByDefault(because = "Explicitly publishes reviewed release evidence into consumer source directories")
abstract class PublishSqliteMagicRelease : SqliteMagicReleaseTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  abstract val compiledSchema: RegularFileProperty

  @TaskAction
  fun publish() {
    checkSelectedRelease("publish authoritative history")
    val captured = compiledSchema.orNull?.asFile?.let(::readSchema)
    check(captured == null || captured.releaseVariant == releaseVariant.get()) {
      "Compiled schema belongs to a different selected release variant"
    }
    checkGeneratedMetadata()
    ReleaseMigrationCoordinator.migrate(
      projectDir = projectDirectory.get().asFile,
      databaseDirectory = databaseDirectory.get().asFile,
      variantName = assetsBuildType.get(),
      databaseVersion = databaseVersion(),
      currentStructureFiles = currentStructureFiles(),
      compiledRelease = captured,
      evidenceAssetsDirectory = evidenceAssetsDirectory(),
      warn = logger::warn
    )
  }
}

@DisableCachingByDefault(because = "Previews the next release from mutable release evidence and source assets")
abstract class PreviewSqliteMagicReleaseMigration : SqliteMagicReleaseTask() {
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun preview() {
    checkSelectedRelease("preview the next release")
    checkGeneratedMetadata()
    val preview = ReleaseMigrationCoordinator.preview(
      projectDir = projectDirectory.get().asFile,
      databaseDirectory = databaseDirectory.get().asFile,
      variantName = assetsBuildType.get(),
      databaseVersion = databaseVersion(),
      currentStructureFiles = currentStructureFiles(),
      evidenceAssetsDirectory = evidenceAssetsDirectory()
    )
    val output = outputDirectory.get().asFile
    check(output.deleteRecursively() && output.mkdirs()) {
      "Failed to replace SqliteMagic release migration preview ${output.absolutePath}"
    }
    output
      .resolve("report.txt")
      .writeText("${preview.report}\n")
    preview.sql?.let(output.resolve("${preview.version}.sql")::writeText)
    logger.lifecycle(preview.report)
    logger.lifecycle("\nSaved release migration preview to ${output.absolutePath}")
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
  databaseId: String,
  releaseVariant: String,
  currentVersion: Int,
  namespace: String,
  migrationTaskPath: String
) = $$"""
package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.migration.MigrationDatabaseDescriptor
import java.io.FileNotFoundException
import java.io.InputStream

object SqliteMagicMigrationDatabase : MigrationDatabaseDescriptor {
  override val databaseId = $${kotlinLiteral(databaseId)}
  override val releaseVariant = $${kotlinLiteral(releaseVariant)}
  override val currentVersion = $$currentVersion
  override val resourceRoot = $${kotlinLiteral(namespace)}
  override val manifestResource = "manifest.json"
  override val currentSchemaResource = "current.schema.json"
  override val migrationTaskPath = $${kotlinLiteral(migrationTaskPath)}

  override fun openResource(name: String): InputStream =
    javaClass.classLoader?.getResourceAsStream(resourceRoot + name)
      ?: throw FileNotFoundException("Missing migration test resource: $resourceRoot$name")
}
""".trimIndent() + "\n"

private fun encodedByte(byte: Byte) = "%" + (byte.toInt() and 0xff)
  .toString(16)
  .padStart(
    length = 2,
    padChar = '0'
  )
