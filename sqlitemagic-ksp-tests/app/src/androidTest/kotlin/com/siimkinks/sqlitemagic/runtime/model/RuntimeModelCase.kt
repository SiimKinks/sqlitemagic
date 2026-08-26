package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

interface RuntimeModelCase<T> {
  val name: String
  val table: Table<T>

  fun newValue(sequence: Int): T
}
