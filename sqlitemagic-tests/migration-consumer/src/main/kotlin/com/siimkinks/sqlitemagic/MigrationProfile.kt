package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

data class MigrationContact(
  val city: String?,
  val phone: String?,
  val postalCode: String?
)

data class MigrationEmail(val value: String)

@ObjectToDbValue
fun migrationEmailToString(email: MigrationEmail): String = email.value

@DbValueToObject
fun stringToMigrationEmail(value: String) = MigrationEmail(value)

@Table("migration_profile")
data class MigrationProfile(
  @Id(autoIncrement = false)
  val id: Long,
  @Embedded(prefix = "contact_")
  val contact: MigrationContact?,
  val email: MigrationEmail,
  @Column(defaultValue = "0.0")
  val rating: Double,
  val avatar: ByteArray?
)
