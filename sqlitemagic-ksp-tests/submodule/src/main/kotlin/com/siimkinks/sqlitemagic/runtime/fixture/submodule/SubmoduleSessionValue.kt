package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

@Table(options = [TEMPORARY])
data class SubmoduleSessionValue(
  @Id val id: String,
  @Index("submodule_session_value_index")
  val value: String
)
