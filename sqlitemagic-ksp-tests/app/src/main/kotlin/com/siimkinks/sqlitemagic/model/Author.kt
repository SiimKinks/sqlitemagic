package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.Random

@Table
data class Author(
  @Id var id: Long? = null,
  var name: String? = null,
  var boxedBoolean: Boolean? = null,
  var primitiveBoolean: Boolean = false
) {
  fun fillWithRandomValues() {
    val r = Random()
    id = r.nextLong()
    name = Utils.randomTableName()
    boxedBoolean = r.nextBoolean()
    primitiveBoolean = r.nextBoolean()
  }

  companion object {
    fun newRandom() = Author().also(Author::fillWithRandomValues)
  }
}
