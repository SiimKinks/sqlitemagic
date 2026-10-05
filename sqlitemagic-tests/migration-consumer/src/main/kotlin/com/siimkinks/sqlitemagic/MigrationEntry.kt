package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table("migration_entry")
@Index(
  value = "migration_entry_account_reference",
  unique = true
)
data class MigrationEntry(
  @Id
  val id: Long? = null,
  @Column(
    onDeleteCascade = true,
    belongsToIndex = "migration_entry_account_reference"
  )
  val account: MigrationAccount,
  @Column(belongsToIndex = "migration_entry_account_reference")
  val reference: String,
  @Column(handleRecursively = false)
  val person: MigrationPerson?,
  @Column(defaultValue = "0.0")
  val amount: Double,
  @Column(defaultValue = "'posted'")
  val status: String
)
