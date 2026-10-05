package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.annotation.Unique

@Table(
  value = "migration_account",
  options = [WITHOUT_ROWID]
)
data class MigrationAccount(
  @Id(autoIncrement = false)
  val id: String,
  @Unique
  val label: String
)
