package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class PreparedRelease(
  val version: Int,
  val previousVersion: Int?,
  val migration: SchemaMigration?,
  val structureFile: File,
  val structure: DatabaseStructure,
  val sqlFile: File,
  val sql: String?
) {
  val generatesSql get() = migration?.plan != null
  val warnings get() = migration?.warnings.orEmpty()

  fun publish(transaction: FileSnapshotTransaction) {
    transaction.writeTextIfChanged(
      file = structureFile,
      text = DatabaseStructureJson.write(structure)
    )
    if (sql != null) {
      transaction.writeTextIfChanged(
        file = sqlFile,
        text = sql
      )
    }
  }
}
