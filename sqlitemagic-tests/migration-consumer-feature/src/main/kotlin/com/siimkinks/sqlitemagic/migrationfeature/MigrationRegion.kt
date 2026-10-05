package com.siimkinks.sqlitemagic.migrationfeature

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table("migration_region")
data class MigrationRegion(
  @Id(autoIncrement = false) val id: String,
  val label: String,
  @Column(defaultValue = "'global'") val country: String = "global"
)
