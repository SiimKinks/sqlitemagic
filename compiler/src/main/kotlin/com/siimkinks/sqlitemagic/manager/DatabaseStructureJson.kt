package com.siimkinks.sqlitemagic.manager

import kotlinx.serialization.json.Json
import java.io.File

internal object DatabaseStructureJson {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  fun read(file: File): DatabaseStructure? = runCatching {
    read(file.readText())
  }.getOrNull()

  fun read(value: String) = json
    .decodeFromString<DatabaseStructure>(value)
    .withQuotedIdentifiers()

  fun write(
    file: File,
    structure: DatabaseStructure
  ) {
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(structure))
  }

  fun write(structure: DatabaseStructure): String = json.encodeToString(structure)
}
