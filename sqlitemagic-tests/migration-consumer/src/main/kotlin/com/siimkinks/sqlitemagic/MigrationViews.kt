package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.MigrationAccountTable.Companion.MIGRATION_ACCOUNT
import com.siimkinks.sqlitemagic.MigrationEntryTable.Companion.MIGRATION_ENTRY
import com.siimkinks.sqlitemagic.MigrationPersonTable.Companion.MIGRATION_PERSON
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("migration_person_summary")
data class MigrationPersonSummary(
  @ViewColumn("id")
  val id: Long,
  @ViewColumn("name")
  val name: String,
  @ViewColumn("slug")
  val slug: String?,
  @ViewColumn("name_length")
  val nameLength: Int?
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMNS(
        MIGRATION_PERSON.ID AS "id",
        MIGRATION_PERSON.NAME AS "name",
        MIGRATION_PERSON.SLUG AS "slug",
        MIGRATION_PERSON.NAME_LENGTH AS "name_length"
      )
      .FROM(MIGRATION_PERSON)
      .WHERE(MIGRATION_PERSON.SLUG.IS_NOT_NULL())
  }
}

@View("migration_entry_summary")
data class MigrationEntrySummary(
  @ViewColumn("id")
  val id: Long?,
  @ViewColumn("amount")
  val amount: Double,
  @ViewColumn("label")
  val accountLabel: String,
  @ViewColumn("status")
  val status: String,
  @ViewColumn("reference")
  val reference: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMNS(
        MIGRATION_ENTRY.ID AS "id",
        MIGRATION_ENTRY.AMOUNT AS "amount",
        MIGRATION_ACCOUNT.LABEL AS "label",
        MIGRATION_ENTRY.STATUS AS "status",
        MIGRATION_ENTRY.REFERENCE AS "reference"
      )
      .FROM(MIGRATION_ENTRY)
  }
}
