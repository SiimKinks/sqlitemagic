package com.siimkinks.sqlitemagic.manager

import java.io.File
import kotlinx.serialization.json.Json

internal object DatabaseStructureJson {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  fun read(file: File): DatabaseStructure? = runCatching {
    json.decodeFromString<DatabaseStructure>(file.readText())
  }.getOrNull()

  fun read(value: String): DatabaseStructure =
    json.decodeFromString(value)

  fun write(
    file: File,
    structure: DatabaseStructure
  ) {
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(structure))
  }

  fun write(structure: DatabaseStructure): String = json.encodeToString(structure)
}
