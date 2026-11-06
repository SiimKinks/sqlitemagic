package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class CascadingNote(
  @Id val id: Long? = null,
  val text: String,
  @Column(handleRecursively = false, onDeleteCascade = true) val entity: EntityWithRelationship
)
