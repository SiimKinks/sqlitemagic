package com.siimkinks.sqlitemagic

abstract class SqlNode internal constructor(
  internal val parent: SqlNode?
) : SqlClause()
