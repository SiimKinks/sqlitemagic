package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table("library_books")
data class LibraryBook(
  @Id
  @Column("book_key")
  val id: String,
  @Column(
    value = "title_text",
    defaultValue = "'untitled'"
  )
  val title: String? = null
)
