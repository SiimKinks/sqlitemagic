package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class ComplexObjectWithSameLeafs(
  @Id val id: Long,
  val name: String,
  @Column
  val simpleValue: ImmutableValueWithFields,
  val entityWithRelationship: EntityWithRelationship,
  val simpleValueDuplicate: ImmutableValueWithFields
)
