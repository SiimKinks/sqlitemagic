package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal object SqlCreator {
  fun getSql(
    sqlNode: SqlNode,
    sqlNodeCount: Int,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>? = null
  ) = buildString(capacity = sqlNodeCount * 20) {
    appendSql(
      sqlNode = sqlNode,
      stringBuilder = this,
      systemRenamedTables = systemRenamedTables
    )
  }

  private fun appendSql(
    sqlNode: SqlNode,
    stringBuilder: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>?
  ) {
    sqlNode.parent?.let { parent ->
      appendSql(
        sqlNode = parent,
        stringBuilder = stringBuilder,
        systemRenamedTables = systemRenamedTables
      )
    }
    when (systemRenamedTables) {
      null -> sqlNode.appendSql(stringBuilder)
      else -> sqlNode.appendSql(stringBuilder, systemRenamedTables)
    }
    stringBuilder.append(' ')
  }
}
