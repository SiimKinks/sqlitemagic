package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

@Table(options = [TEMPORARY])
data class MainSessionValue(
  @Id val id: String,
  val value: String
)
