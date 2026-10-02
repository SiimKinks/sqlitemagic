package com.siimkinks.sqlitemagic.runtime

import com.siimkinks.sqlitemagic.SqliteMagic

internal fun namespaceContractWritableDatabase() = SqliteMagic
  .getDefaultDbConnection()
  .writableDatabase
