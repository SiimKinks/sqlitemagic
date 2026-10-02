package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("report.v1")
data class DottedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("report space")
data class SpacedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("report\"quote")
data class QuotedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("select")
data class KeywordIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("report!bang")
data class PunctuationIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("report\u00A0space")
data class NonbreakingSpaceIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("report-ok")
data class HyphenIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("café")
data class AccentedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}
