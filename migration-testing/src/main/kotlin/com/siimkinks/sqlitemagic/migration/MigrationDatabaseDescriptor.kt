package com.siimkinks.sqlitemagic.migration

import java.io.InputStream

interface MigrationDatabaseDescriptor {
  val databaseId: String
  val releaseVariant: String
  val currentVersion: Int
  val resourceRoot: String
  val manifestResource: String
  val currentSchemaResource: String

  /** Publication task named in migration-test diagnostics; generated descriptors include the full project path. */
  val migrationTaskPath: String
    get() = "migrate${releaseVariant.replaceFirstChar(Char::uppercaseChar)}Db"

  fun openResource(name: String): InputStream
}
