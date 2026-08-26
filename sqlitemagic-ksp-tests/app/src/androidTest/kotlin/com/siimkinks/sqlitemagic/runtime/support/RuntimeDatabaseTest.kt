package com.siimkinks.sqlitemagic.runtime.support

import com.siimkinks.sqlitemagic.SqliteMagic
import org.junit.Before

abstract class RuntimeDatabaseTest {
  @Before
  fun clearDatabase() {
    SqliteMagic
      .getDefaultConnection()
      .clearData()
  }
}
