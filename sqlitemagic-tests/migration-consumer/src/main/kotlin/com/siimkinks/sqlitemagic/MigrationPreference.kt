package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table(
  value = "migration_setting",
  persistAll = false
)
@Index("migration_preference_lookup")
data class MigrationPreference(
  @Unique
  @Column(belongsToIndex = "migration_preference_lookup")
  val key: String,
  @Column(belongsToIndex = "migration_preference_lookup")
  val value: String
)
