package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

@Table(options = [TEMPORARY])
data class MainSessionValue(
  @Id val id: String,
  @Index("main_session_value_index")
  val value: String
)
