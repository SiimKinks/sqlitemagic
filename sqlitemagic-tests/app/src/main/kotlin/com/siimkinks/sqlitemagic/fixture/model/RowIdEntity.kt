package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.RowId
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class RowIdEntity(
  @Id val id: String,
  @RowId val rowId: Long? = null,
  val value: String
)
