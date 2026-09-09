package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.SubmoduleDatabaseMetadata
import java.io.File
import java.util.Locale

internal fun validateConfiguredSubmoduleStructures(
  environment: Environment,
  database: GeneratedDatabaseElement,
  currentStructure: DatabaseStructure
): Boolean {
  if (database.isSubmodule || database.submodules.isEmpty()) return true
  val projectDir = environment.options.projectDir ?: return true
  val stagedDirectories = environment.options.structureInputDirectories
    .map(::File)
    .ifEmpty { listOf(File(projectDir, "db")) }
  val structures = buildList {
    add("main" to currentStructure)
    database.submodules
      .sortedBy(SubmoduleDatabaseMetadata::moduleName)
      .forEach { submodule ->
        val fileName = "latest_${submodule.moduleName.lowercase(Locale.ROOT)}.struct"
        val stagedFiles = stagedDirectories
          .map { it.resolve(fileName) }
          .filter(File::isFile)
        when {
          stagedFiles.isEmpty() -> {
            environment.logger.error(
              "Missing staged current structure snapshot for configured submodule " +
                  "'${submodule.moduleName}' in variant '${environment.options.variantName.orEmpty()}': " +
                  stagedDirectories.joinToString { it.absolutePath }
            )
            return false
          }
          stagedFiles.size > 1 -> {
            environment.logger.error(
              "Multiple staged current structure snapshots for configured submodule " +
                  "'${submodule.moduleName}': ${stagedFiles.joinToString { it.absolutePath }}"
            )
            return false
          }
        }
        val file = stagedFiles.single()
        val structure = runCatching {
          DatabaseStructureJson.read(file.readText())
        }.getOrElse { exception ->
          environment.logger.error(
            "Malformed current structure snapshot for configured submodule " +
                "'${submodule.moduleName}': ${exception.message.orEmpty()}"
          )
          return false
        }
        add(submodule.moduleName to structure)
      }
  }
  val conflicts = findSchemaIdentityConflicts(structures)
  conflicts.forEach { conflict ->
    environment.logger.error(conflict.run {
      "Duplicate SQLite schema identifier '$name' in ${namespace.displayName} namespace: " +
          "${objectKind.label} '$name' from $source conflicts with " +
          "${previousOwner.objectKind.label} '${previousOwner.name}' from ${previousOwner.source}"
    })
  }
  return conflicts.isEmpty()
}
