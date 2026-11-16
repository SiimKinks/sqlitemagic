package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class RowIdChild(
  @Id val id: Long? = null,
  @Column(onDeleteCascade = true) val parent: RowIdEntity,
  val label: String
)
