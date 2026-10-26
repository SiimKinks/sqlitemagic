package com.siimkinks.sqlitemagic.runtime.support

import com.siimkinks.sqlitemagic.SqliteMagic
import org.junit.rules.ExternalResource

class ClearDatabaseRule : ExternalResource() {
  override fun before() = SqliteMagic
    .getDefaultConnection()
    .clearData()
}
