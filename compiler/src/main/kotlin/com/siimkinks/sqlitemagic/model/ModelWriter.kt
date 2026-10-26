package com.siimkinks.sqlitemagic.model

internal interface ModelWriter {
  fun write(tableRoundElement: TableRoundElement)
}