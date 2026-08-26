package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID

@Table(options = [WITHOUT_ROWID])
data class WithoutRowIdEntity(
  @Id val id: String,
  val value: String
)
