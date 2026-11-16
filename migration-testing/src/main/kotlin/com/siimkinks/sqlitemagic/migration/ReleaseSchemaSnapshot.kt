package com.siimkinks.sqlitemagic.migration

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.Serializable

@Serializable
data class MigrationConfiguration(
  val foreignKeysEnabled: Boolean,
  @EncodeDefault(NEVER)
  val recursiveTriggersEnabled: Boolean = false
)

@Serializable
data class MigrationViewSchema(val name: String, val createSql: String)

/**
 * Schema of a released database version. Compiled current exports include views; retained structures provide
 * tables, indexes and full-text indexes.
 */
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
  val indexSql: List<String>,
  @EncodeDefault(NEVER)
  val virtualTableSql: List<String> = emptyList(),
  @EncodeDefault(NEVER)
  val triggerSql: List<String> = emptyList()
) {
  val viewSql get() = views.map(MigrationViewSchema::createSql)
  val creationSql get() = tableSql + viewSql + indexSql + virtualTableSql + triggerSql

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
    creationSql.forEach { sql ->
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
