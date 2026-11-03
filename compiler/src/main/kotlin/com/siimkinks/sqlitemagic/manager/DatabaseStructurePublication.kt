package com.siimkinks.sqlitemagic.manager

import java.io.File

object DatabaseStructurePublication {
  fun load(file: File) = runCatching {
    val bytes = file.readBytes()
    Snapshot(
      sourceBytes = bytes,
      structure = DatabaseStructureJson.read(bytes.decodeToString())
    )
  }.getOrElse {
    throw IllegalStateException("Malformed current database structure snapshot ${file.absolutePath}", it)
  }

  class Snapshot internal constructor(
    private val sourceBytes: ByteArray,
    structure: DatabaseStructure
  ) {
    private val persistentStructure = structure.persistentOnly()

    val hasPersistentObjects = with(persistentStructure) {
      tables.isNotEmpty() || indices.isNotEmpty() || views.isNotEmpty()
    }

    fun hasPersistentChanges(other: Snapshot) = !persistentStructure.sameSchemaAs(other.persistentStructure)

    fun writeTo(target: File) {
      target.parentFile?.mkdirs()
      target.writeBytes(sourceBytes)
    }
  }
}
