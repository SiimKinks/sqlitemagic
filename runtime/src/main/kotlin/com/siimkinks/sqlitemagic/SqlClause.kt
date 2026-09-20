package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

abstract class SqlClause {
  abstract fun appendSql(
    sb: StringBuilder
  )

  abstract fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  )
}