package com.siimkinks.sqlitemagic.fixture.model.fulltext

import com.siimkinks.sqlitemagic.annotation.FullText
import com.siimkinks.sqlitemagic.annotation.FullTextIndex
import com.siimkinks.sqlitemagic.annotation.FullTextRole.LANGUAGE_ID
import com.siimkinks.sqlitemagic.annotation.FullTextRole.UNINDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class LetterAuthor(
  @Id(autoIncrement = false) val id: Long,
  val name: String
)

@Table
@FullTextIndex(version = FTS4, tokenize = "porter")
data class Letter(
  @Id(autoIncrement = false) val id: Long,
  @FullText val subject: String,
  @FullText val body: String?,
  @FullText(role = UNINDEXED) val tag: String,
  @FullText(role = LANGUAGE_ID) val language: Int,
  val author: LetterAuthor
)
