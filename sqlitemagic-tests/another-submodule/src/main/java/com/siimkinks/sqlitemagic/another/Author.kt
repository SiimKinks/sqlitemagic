package com.siimkinks.sqlitemagic.another

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table
data class Author(
    @Id val id: Long? = null,
    val firstName: String,
    @Index
    val lastName: String,
    val booksWritten: Int,
    val active: Boolean
) {
  companion object {
    fun newRandom(): Author {
      val r = Random()
      return Author(
          id = r.nextLong(),
          firstName = Utils.randomTableName(),
          lastName = Utils.randomTableName(),
          booksWritten = r.nextInt(),
          active = r.nextBoolean()
      )
    }
  }
}