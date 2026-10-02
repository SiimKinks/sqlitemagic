package com.siimkinks.sqlitemagic

internal fun namespaceContractWritableDatabase() =
  (SqliteMagic.getDefaultConnection() as DbConnectionImpl)
    .writableDatabase
