package com.siimkinks.sqlitemagic.manager

import java.io.File

object DatabaseStructurePublication {
  fun load(file: File) = runCatching {
    val bytes = file.readBytes()
    Snapshot(
      bytes = bytes,
      structure = DatabaseStructureJson.read(bytes.decodeToString())
    )
  }.getOrElse {
    throw IllegalStateException("Malformed current database structure snapshot ${file.absolutePath}", it)
  }

  class Snapshot internal constructor(
    bytes: ByteArray,
    structure: DatabaseStructure
  ) {
    private val sourceBytes = bytes.copyOf()
    private val persistentStructure = structure.persistentOnly()

    val hasPersistentObjects = with(persistentStructure) {
      tables.isNotEmpty() || indices.isNotEmpty() || views.isNotEmpty()
    }
    val persistentViewNames = persistentStructure.views.keys.toList()

    fun hasPersistentChanges(other: Snapshot) = persistentStructure != other.persistentStructure

    fun writeTo(target: File) {
      target.parentFile?.mkdirs()
      target.writeBytes(sourceBytes)
    }
  }
}
