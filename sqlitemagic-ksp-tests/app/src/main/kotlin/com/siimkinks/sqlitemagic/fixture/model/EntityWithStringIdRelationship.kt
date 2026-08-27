package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class EntityWithStringIdRelationship(
  @Id val id: Long?,
  val value: String,
  val relatedEntity: StringIdEntity
)
