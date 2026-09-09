package com.siimkinks.sqlitemagic.manager

import java.io.File

object DatabaseStructurePublication {
  fun hasPersistentChanges(
    previousFile: File,
    currentFile: File
  ) = readRequired(previousFile).persistentOnly() != readRequired(currentFile).persistentOnly()

  fun hasPersistentObjects(file: File) =
    readRequired(file)
      .let { structure ->
        structure.tables.isNotEmpty() || structure.indices.isNotEmpty()
      }

  private fun readRequired(file: File) = DatabaseStructureJson
    .read(file)
    ?: error("Malformed current database structure snapshot ${file.absolutePath}")
}