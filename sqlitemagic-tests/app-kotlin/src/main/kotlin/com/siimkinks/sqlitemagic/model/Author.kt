package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table
class Author {
  @Id
  @JvmField
  var id: Long? = null
  @Column
  @JvmField
  var name: String? = null
  @Column
  @JvmField
  var boxedBoolean: Boolean? = null
  @Column
  @JvmField
  var primitiveBoolean: Boolean = false

  companion object {
    fun newRandom(): Author {
      val author = Author()
      fillWithRandomValues(author)
      return author
    }

    fun fillWithRandomValues(author: Author) {
      val r = Random()
      author.id = r.nextLong()
      author.name = Utils.randomTableName()
      author.boxedBoolean = r.nextBoolean()
      author.primitiveBoolean = r.nextBoolean()
    }
  }
}