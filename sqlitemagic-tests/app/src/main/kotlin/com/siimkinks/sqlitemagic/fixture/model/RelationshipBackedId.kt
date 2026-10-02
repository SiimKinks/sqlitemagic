package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class LeafId(
  @Id val value: String?
)

@Table
data class RelationshipId(
  @Id
  @Column(handleRecursively = false)
  val id: LeafId
)

@Table
data class RelationshipOwner(
  @Id val ownerId: String,
  @Column(handleRecursively = false)
  val relationshipId: RelationshipId,
  val value: String
)
