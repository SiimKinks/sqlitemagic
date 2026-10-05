package com.siimkinks.sqlitemagic.migration

import java.io.InputStream

interface MigrationDatabaseDescriptor {
  val databaseId: String
  val releaseVariant: String
  val currentVersion: Int
  val resourceRoot: String
  val manifestResource: String
  val currentSchemaResource: String
  val firstVersion: Int?
  fun openResource(name: String): InputStream
}
