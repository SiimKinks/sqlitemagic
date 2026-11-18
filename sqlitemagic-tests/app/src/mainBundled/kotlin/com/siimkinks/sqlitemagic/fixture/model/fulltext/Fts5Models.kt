package com.siimkinks.sqlitemagic.fixture.model.fulltext

import com.siimkinks.sqlitemagic.annotation.FullText
import com.siimkinks.sqlitemagic.annotation.FullTextIndex
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.RowId
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class SearchAuthor(
  @Id(autoIncrement = false) val id: Long,
  val name: String
)

@Table
@FullTextIndex
data class SearchBook(
  @Id(autoIncrement = false) val id: Long,
  @FullText val title: String,
  @FullText val summary: String,
  val author: SearchAuthor
)

@Table
@FullTextIndex
data class SearchNote(
  @Id val code: String,
  @RowId val rowId: Long? = null,
  @FullText val text: String
)
