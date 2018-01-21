package com.siimkinks.sqlitemagic.multimodule

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table(persistAll = true, useAccessMethods = true)
data class Author(
    @Id val id: Long? = null,
    val firstName: String,
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