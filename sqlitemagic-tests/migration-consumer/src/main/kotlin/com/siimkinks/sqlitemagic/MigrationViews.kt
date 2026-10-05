package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.MigrationAccountTable.Companion.MIGRATION_ACCOUNT
import com.siimkinks.sqlitemagic.MigrationEntryTable.Companion.MIGRATION_ENTRY
import com.siimkinks.sqlitemagic.MigrationPersonTable.Companion.MIGRATION_PERSON
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("migration_person_summary")
data class MigrationPersonSummary(
  @ViewColumn("migration_person.id")
  val id: Long,
  @ViewColumn("migration_person.name")
  val name: String,
  @ViewColumn("migration_person.slug")
  val slug: String?,
  @ViewColumn("migration_person.name_length")
  val nameLength: Int?
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        MIGRATION_PERSON.ID,
        MIGRATION_PERSON.NAME,
        MIGRATION_PERSON.SLUG,
        MIGRATION_PERSON.NAME_LENGTH
      )
      .from(MIGRATION_PERSON)
      .where(MIGRATION_PERSON.SLUG.isNotNull())
      .compile()
  }
}

@View("migration_entry_summary")
data class MigrationEntrySummary(
  @ViewColumn("migration_entry.id")
  val id: Long?,
  @ViewColumn("migration_entry.amount")
  val amount: Double,
  @ViewColumn("migration_account.label")
  val accountLabel: String,
  @ViewColumn("migration_entry.status")
  val status: String,
  @ViewColumn("migration_entry.reference")
  val reference: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        MIGRATION_ENTRY.ID,
        MIGRATION_ENTRY.AMOUNT,
        MIGRATION_ACCOUNT.LABEL,
        MIGRATION_ENTRY.STATUS,
        MIGRATION_ENTRY.REFERENCE
      )
      .from(MIGRATION_ENTRY)
      .compile()
  }
}
