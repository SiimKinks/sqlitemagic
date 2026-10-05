package com.siimkinks.sqlitemagic.migration

import kotlinx.serialization.Serializable

@Serializable
data class MigrationConfiguration(val foreignKeysEnabled: Boolean)

@Serializable
data class MigrationViewSchema(val name: String, val createSql: String)

/** Host schema capture; compiled current exports include views, retained structures provide tables and indexes. */
@Serializable
data class ReleaseSchemaSnapshot(
  val formatVersion: Int,
  val databaseId: String,
  val releaseVariant: String,
  val version: Int,
  val modules: List<String>,
  val configuration: MigrationConfiguration,
  val tableSql: List<String>,
  val views: List<MigrationViewSchema>,
  val indexSql: List<String>
) {
  val viewSql get() = views.map(MigrationViewSchema::createSql)

  fun validate() {
    require(formatVersion == 1) { "Unsupported release schema format $formatVersion" }
    validateReleaseIdentity(
      databaseId = databaseId,
      releaseVariant = releaseVariant,
      modules = modules
    )
    require(version > 0) { "Invalid release schema version $version" }
    require(views.all { it.name.isNotBlank() }) { "Release schema $version contains an empty generated view name" }
    require(views.map(MigrationViewSchema::name).distinct().size == views.size) {
      "Release schema $version contains duplicate generated views"
    }
    (tableSql + viewSql + indexSql).forEach { sql ->
      require(sql.isNotBlank()) { "Release schema $version contains empty creation SQL" }
    }
  }
}

internal fun validateReleaseIdentity(
  databaseId: String,
  releaseVariant: String,
  modules: List<String>
) {
  require(databaseId.isNotBlank()) { "Database identity must not be blank" }
  require(releaseVariant.isNotBlank()) { "Selected release variant must not be blank" }
  require(modules.isNotEmpty() && modules.all(String::isNotBlank)) { "Module identities must not be empty" }
  require(modules.distinct().size == modules.size) { "Duplicate release module identities: $modules" }
  require(modules.last() == databaseId) { "Main database module '$databaseId' must be last" }
}
