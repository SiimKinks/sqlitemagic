package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table("migration_person")
data class MigrationPerson(
  @Id(autoIncrement = false)
  val id: Long,
  val name: String = "",
  @Column("name_length")
  val nameLength: Int? = null,
  @Column("slug")
  @Index(
    value = "migration_person_slug_unique",
    unique = true
  )
  val slug: String? = null
)
