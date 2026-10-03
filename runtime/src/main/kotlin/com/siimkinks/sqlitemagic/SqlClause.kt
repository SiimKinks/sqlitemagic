package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import java.util.LinkedList

abstract class SqlClause {
  abstract fun appendSql(
    sb: StringBuilder
  )

  abstract fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: MutableScatterMap<String, LinkedList<String>>
  )
}