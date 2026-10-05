package com.siimkinks.sqlitemagic.migration.export

import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import java.io.File
import java.lang.reflect.InvocationTargetException

internal object CompiledSchemaExporter {
  @JvmStatic
  fun main(args: Array<String>) {
    val schema = export(
      generatedDatabaseClassName = args[0],
      databaseId = args[1],
      releaseVariant = args[2]
    )
    val output = File(args[3])
    check(output.parentFile?.let { it.isDirectory || it.mkdirs() } != false) {
      "Cannot create schema export directory ${output.parentFile}"
    }
    output.writeText(MigrationMetadataJson.writeSchema(schema))
  }

  @Synchronized
  fun export(
    generatedDatabaseClassName: String,
    databaseId: String,
    releaseVariant: String
  ): ReleaseSchemaSnapshot {
    check(ExportRuntimeState.defaultConnection == null) {
      "Schema export requires an isolated JVM without an open database"
    }
    val previousDatabase = ExportRuntimeState.database
    val previousLogging = ExportRuntimeState.loggingEnabled
    try {
      ExportRuntimeState.loggingEnabled = false
      val generated = Class.forName(generatedDatabaseClassName)
        .getDeclaredConstructor()
        .newInstance() as GeneratedDatabase
      require(!generated.isDebug) { "Schema export must use a selected release database, not a debug database" }
      ExportRuntimeState.database = generated
      val recorder = SchemaRecordingDatabase()
      generated.configureDatabase(recorder.database)
      generated.createSchema(recorder.database)
      check(ExportRuntimeState.defaultConnection == null) { "Schema query initialization opened a database connection" }
      return recorder.snapshot(
        databaseId = databaseId,
        releaseVariant = releaseVariant,
        version = generated.dbVersion,
        modules = generated.submoduleNames.orEmpty().toList() + databaseId
      )
    } catch (failure: Exception) {
      throw exportFailure(
        failure = failure,
        generatedDatabaseClassName = generatedDatabaseClassName,
        releaseVariant = releaseVariant
      )
    } catch (failure: LinkageError) {
      throw exportFailure(
        failure = failure,
        generatedDatabaseClassName = generatedDatabaseClassName,
        releaseVariant = releaseVariant
      )
    } finally {
      ExportRuntimeState.database = previousDatabase
      ExportRuntimeState.loggingEnabled = previousLogging
    }
  }

  private fun exportFailure(
    failure: Throwable,
    generatedDatabaseClassName: String,
    releaseVariant: String
  ): IllegalStateException {
    val cause = when (failure) {
      is InvocationTargetException -> failure.targetException
      else -> failure
    }
    if (cause is VirtualMachineError) throw cause
    return IllegalStateException(
      "Cannot export selected release '$releaseVariant' from $generatedDatabaseClassName. " +
          "Generated schema and query owners must initialize without application services or Android execution.",
      cause
    )
  }
}

/** Access is confined to this isolated host exporter; runtime exposes no tooling bridge. */
internal object ExportRuntimeState {
  private val databaseField = SqliteMagic::class.java.getDeclaredField("database").apply { isAccessible = true }
  private val connectionField = SqliteMagic::class.java.getDeclaredField("defaultConnection")
    .apply { isAccessible = true }

  private val loggingField = SqliteMagic::class.java.getDeclaredField("loggingEnabled").apply { isAccessible = true }

  var loggingEnabled: Boolean
    get() = loggingField.getBoolean(null)
    set(value) = loggingField.setBoolean(null, value)

  var database: GeneratedDatabase?
    get() = databaseField.get(null) as GeneratedDatabase?
    set(value) = databaseField.set(null, value)

  val defaultConnection: Any? get() = connectionField.get(null)
}
