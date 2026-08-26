package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class CustomColumnEntity(
  @Id
  @Column("_id")
  var id: Long? = null,
  @Column("stored_value")
  val value: String
)
