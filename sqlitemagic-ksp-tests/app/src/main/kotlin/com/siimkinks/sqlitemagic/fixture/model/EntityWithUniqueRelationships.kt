package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table
data class UniqueRelatedEntity(
  @Id var id: Long? = null,
  @Unique val uniqueValue: String,
  val value: String
)

@Table
data class EntityWithUniqueRelationships(
  @Id var id: Long? = null,
  @Unique val uniqueValue: String,
  val firstRelatedEntity: UniqueRelatedEntity,
  val secondRelatedEntity: UniqueRelatedEntity
)
