package com.siimkinks.sqlitemagic.structure

import com.google.common.base.Joiner
import com.google.common.io.Files
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep
import com.siimkinks.sqlitemagic.util.JsonConfig
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Paths

class MigrationsHandler(
    private val currentStructure: DatabaseStructure,
    private val previousStructure: DatabaseStructure?,
    private val outputStructureFile: File,
    private val migrationOutputFile: File
) {
  companion object {
    fun handleDebugMigrations(environment: Environment, managerStep: GenClassesManagerStep) {
      if (environment.isDebugVariant && environment.isMigrateDebug) {
        val nextDbVersion = readLatestDebugVersion(environment).inc()
        val structureFile = File(environment.projectDir, "db")
            .resolve("latest.struct")
        val assetsDir = Paths.get(
            environment.projectDir,
            "src", environment.variantName, "assets")
            .toFile()
        val migrationFileName = "$nextDbVersion.sql".let {
          when {
            environment.isSubmodule -> environment.submoduleName + it
            else -> it
          }
        }
        val currentStructure = DatabaseStructure.create(environment, managerStep)

        try {
          val migrationHappened = MigrationsHandler(
              currentStructure = currentStructure,
              previousStructure = structureFile.readStructure(),
              outputStructureFile = structureFile,
              migrationOutputFile = File(assetsDir, migrationFileName))
              .migrate()

          if (environment.isSubmodule) {
            environment.mainModulePath?.let { mainModulePath ->
              val submoduleName = checkNotNull(environment.submoduleName).toLowerCase()
              val dbDir = File(mainModulePath, "db")
              val mainModuleStructureFile = dbDir.resolve("latest_$submoduleName.struct")
              JsonConfig.OBJECT_MAPPER.writeValue(mainModuleStructureFile, currentStructure)

              if (migrationHappened) {
                dbDir.resolve("$submoduleName.changed").createNewFile()
              }
            }
          } else {
            val submoduleChangeHappened = determineSubmoduleChange(environment)
            if (migrationHappened || submoduleChangeHappened) {
              environment.dbVersion = nextDbVersion
              writeMainModuleDebugVersion(environment, nextDbVersion)
            }
          }
        } catch (e: IOException) {
          environment.warning("Error persisting latest schema graph $e")
        }
      }
    }

    fun handleReleaseMigrations(
        projectDir: File,
        dbDir: File,
        variantName: String
    ) {
      val releaseStructuresDir = File(dbDir, "releases")
      val latestReleaseFile = releaseStructuresDir
          .takeIf(File::exists)
          ?.let { releaseDir ->
            releaseDir
                .listFiles { file -> !file.isDirectory }
                .asSequence()
                .sortedBy { it.nameWithoutExtension.toIntOrNull() }
                .lastOrNull()
          }
      val releaseAssetsDir = Paths.get(
          projectDir.absolutePath,
          "src", variantName, "assets")
          .toFile()
      val releaseVersion = determineReleaseVersion(
          latestReleaseFile = latestReleaseFile,
          releaseAssetsDir = releaseAssetsDir)
      val currentStructure = dbDir
          .listFiles { file -> file.extension == "struct" }
          .fold(initial = DatabaseStructure()) { acc, structureFile ->
            acc + structureFile.readStructure()
          }

      MigrationsHandler(
          currentStructure = currentStructure,
          previousStructure = latestReleaseFile?.readStructure(),
          outputStructureFile = releaseStructuresDir.resolve("$releaseVersion.struct"),
          migrationOutputFile = File(releaseAssetsDir, "$releaseVersion.sql"))
          .migrate()
    }
  }

  internal fun migrate(): Boolean {
    try {
      val previousStructure = previousStructure
      val currentStructure = currentStructure
      var migrationHappened = false
      if (previousStructure != null && currentStructure != previousStructure) {
        migrateDatabase(from = previousStructure, to = currentStructure)
        migrationHappened = true
      }
      persistStructure(currentStructure)
      return migrationHappened
    } catch (e: Exception) {
      RuntimeException("Failed to automatically migrate database", e).printStackTrace()
      return false
    }
  }

  private fun migrateDatabase(from: DatabaseStructure, to: DatabaseStructure) {
    val migrationStatements = ArrayList<String>()
    val tableMigrationStatements = ArrayList<String>()
    val changedTables = migrateTables(from, to, tableMigrationStatements)
    dropIndices(from, to, changedTables, migrationStatements)
    migrationStatements.addAll(tableMigrationStatements)
    createIndices(from, to, changedTables, migrationStatements)

    persistMigrationStatements(migrationStatements)
  }

  private fun migrateTables(from: DatabaseStructure, to: DatabaseStructure, migrationStatements: ArrayList<String>): Set<String> {
    val fromTables = from.tables
    val toTables = to.tables
    val changedTables = HashSet<String>()
    val newTables = HashSet<String>()

    val fromTablesStructures = destructedTablesStructures(fromTables)
    for ((tableName, toTableStructure) in toTables) {
      if (fromTables.containsKey(tableName)) {
        val fromTableStructure = fromTables[tableName]
        if (toTableStructure != fromTableStructure) {
          changedTables.add(tableName)
          handleTableChange(checkNotNull(fromTableStructure), toTableStructure, migrationStatements)
        } // else unchanged
      } else {
        val changedTableName = fromTablesStructures[toTableStructure.columns]
        if (changedTableName != null) {
          changedTables.add(changedTableName)
          migrationStatements.add("ALTER TABLE $changedTableName RENAME TO $tableName")
        } else {
          // create table
          newTables.add(tableName)
        }
      }
    }

    val removedTables = HashSet(fromTables.keys)
    removedTables.removeAll(toTables.keys)
    removedTables.removeAll(changedTables)
    for (removedTable in removedTables) {
      migrationStatements.add("DROP TABLE IF EXISTS $removedTable")
    }
    changedTables.addAll(removedTables)

    toTables
        .filter { newTables.contains(it.key) }
        .forEach { (_, value) -> migrationStatements.add(value.schema) }

    return changedTables
  }

  private fun handleTableChange(fromTableStructure: TableStructure, toTableStructure: TableStructure, migrationStatements: ArrayList<String>) {
    val fromColumns = fromTableStructure.columns
    val toColumns = toTableStructure.columns
    val tableName = toTableStructure.name
    val fromColumnsSize = fromColumns.size
    val toColumnsSize = toColumns.size
    if (fromColumnsSize < toColumnsSize) {
      var allFromColumnsMatchWithToColumns = true
      for (i in 0 until fromColumnsSize) {
        val fromColumn = fromColumns[i]
        val toColumn = toColumns[i]
        if (fromColumn != toColumn) {
          allFromColumnsMatchWithToColumns = false
          break
        }
      }
      if (allFromColumnsMatchWithToColumns) {
        for (i in fromColumnsSize until toColumnsSize) {
          val newColumn = toColumns[i]
          migrationStatements.add("ALTER TABLE " +
              tableName +
              " ADD COLUMN " +
              newColumn.schema)
        }
        return
      }
    }

    val tmpTableName = tableName + "_"
    migrationStatements.add("ALTER TABLE $tableName RENAME TO $tmpTableName")
    migrationStatements.add(toTableStructure.schema)

    val mutualColumns = Joiner
        .on(',')
        .join(mutualColumns(fromTableStructure, toTableStructure))
    migrationStatements.add("INSERT INTO " +
        tableName +
        " (" +
        mutualColumns +
        ") SELECT " +
        mutualColumns +
        " FROM " +
        tmpTableName)
    migrationStatements.add("DROP TABLE IF EXISTS $tmpTableName")
  }

  private fun destructedTablesStructures(tables: Map<String, TableStructure>): Map<List<ColumnStructure>, String> {
    val result = HashMap<List<ColumnStructure>, String>(tables.size)
    for (tableStructure in tables.values) {
      result[tableStructure.columns] = tableStructure.name
    }
    return result
  }

  private fun mutualColumns(s1: TableStructure, s2: TableStructure): List<String> {
    val s2Columns = HashSet<String>(s2.columns.size)
    for (column in s2.columns) {
      s2Columns.add(column.name)
    }
    val mutualColumns = ArrayList<String>(s1.columns.size)
    for (column in s1.columns) {
      mutualColumns.add(column.name)
    }
    mutualColumns.retainAll(s2Columns)
    return mutualColumns
  }

  private fun dropIndices(from: DatabaseStructure, to: DatabaseStructure, changedTables: Set<String>, migrationStatements: ArrayList<String>) {
    val fromIndices = from.indices
    val toIndices = to.indices
    val droppedIndices = HashSet<String>()
    for (fromIndex in fromIndices.keys) {
      if (!toIndices.containsKey(fromIndex)) {
        droppedIndices.add(fromIndex)
      }
    }
    for (indexStructure in toIndices.values) {
      if (changedTables.contains(indexStructure.forTable)) {
        droppedIndices.add(indexStructure.name)
      }
    }

    for (index in droppedIndices) {
      migrationStatements.add("DROP INDEX IF EXISTS $index")
    }
  }

  private fun createIndices(from: DatabaseStructure, to: DatabaseStructure, changedTables: Set<String>, migrationStatements: ArrayList<String>) {
    val fromIndices = from.indices
    val toIndices = to.indices
    val createdIndices = HashSet<IndexStructure>()
    for ((key, value) in toIndices) {
      if (!fromIndices.containsKey(key)) {
        createdIndices.add(value)
      }
    }
    for (indexStructure in toIndices.values) {
      if (changedTables.contains(indexStructure.forTable)) {
        createdIndices.add(indexStructure)
      }
    }

    for (createdIndex in createdIndices) {
      migrationStatements.add(createdIndex.indexSql)
    }
  }

  private fun persistMigrationStatements(migrationStatements: ArrayList<String>) {
    if (migrationStatements.isEmpty()) {
      return
    }
    val migrationOutputFile = migrationOutputFile
    val migrationOutputFileParent = migrationOutputFile.parentFile
    if (!migrationOutputFileParent.exists()) {
      migrationOutputFileParent.mkdirs()
    }
    try {
      Files.asCharSink(migrationOutputFile, Charset.forName("UTF-8"))
          .writeLines(migrationStatements)
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  private fun persistStructure(structure: DatabaseStructure) {
    val outputStructureFile = outputStructureFile
    val outputStructureFileParent = outputStructureFile.parentFile
    if (!outputStructureFileParent.exists()) {
      outputStructureFileParent.mkdirs()
    }
    JsonConfig.OBJECT_MAPPER.writeValue(outputStructureFile, structure)
  }
}

internal fun File?.readStructure(): DatabaseStructure? {
  if (this == null) return null
  return try {
    JsonConfig.OBJECT_MAPPER.readValue(this, DatabaseStructure::class.java)
  } catch (e: IOException) {
    null
  }
}

internal fun readLatestDebugVersion(environment: Environment): Int {
  val debugVersionFile = File(environment.mainModulePath ?: environment.projectDir, "db")
      .resolve("latest_${environment.variantName}.version")
  return when {
    debugVersionFile.exists() -> debugVersionFile.readLines().last().toInt()
    else -> 1000
  }
}

internal fun writeMainModuleDebugVersion(environment: Environment, version: Int) {
  val versionFileParent = File(environment.projectDir, "db")
  if (!versionFileParent.exists()) {
    versionFileParent.mkdirs()
  }
  versionFileParent
    .resolve("latest_${environment.variantName}.version")
    .writeText(version.toString())
}

private fun determineSubmoduleChange(environment: Environment): Boolean =
    (File(environment.projectDir, "db")
        .listFiles { file -> file.extension == "changed" }
        ?.asSequence()
        ?.map(File::delete)
        ?.count()
        ?: 0) > 0

internal fun determineReleaseVersion(latestReleaseFile: File?, releaseAssetsDir: File): Long {
  val latestReleasedVersion = latestReleaseFile?.nameWithoutExtension
  if (latestReleasedVersion != null) return latestReleasedVersion.toLong() + 1

  return releaseAssetsDir
      .takeIf(File::exists)
      ?.let { releaseDir ->
        releaseDir
            .listFiles { file -> file.extension == "sql" }
            .asSequence()
            .sortedBy { it.name.toIntOrNull() }
            .lastOrNull()
            ?.nameWithoutExtension
            ?.toLong()
            ?.inc()
      }
      ?: 1L
}