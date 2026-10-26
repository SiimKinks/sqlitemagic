package com.siimkinks.sqlitemagic.utils

import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_MAIN_MODULE_PATH
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_MIGRATE_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_PROJECT_DIR
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_STRUCTURE_INPUT_DIRS
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_STRUCTURE_OUTPUT_DIR
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_NAME
import java.io.File
import java.nio.file.Path

fun debugMigrationOptions(
  projectDirectory: Path,
  migrateDebug: Boolean = true,
  mainModuleDirectory: Path? = null,
  structureInputDirectories: List<Path> = emptyList(),
  structureOutputDirectory: Path? = null
) = listOfNotNull(
  OPTION_MIGRATE_DEBUG to migrateDebug.toString(),
  OPTION_PROJECT_DIR to projectDirectory.toString(),
  OPTION_VARIANT_NAME to "debug",
  OPTION_VARIANT_DEBUG to "true",
  mainModuleDirectory?.let { OPTION_MAIN_MODULE_PATH to it.toString() },
  structureInputDirectories
    .takeIf(List<Path>::isNotEmpty)
    ?.let { OPTION_STRUCTURE_INPUT_DIRS to it.joinToString(separator = File.pathSeparator) },
  structureOutputDirectory?.let { OPTION_STRUCTURE_OUTPUT_DIR to it.toString() }
).toMap()
