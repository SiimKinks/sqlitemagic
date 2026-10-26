package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("report.v1")
data class DottedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("report space")
data class SpacedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("""report"quote""")
data class QuotedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("select")
data class KeywordIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("report!bang")
data class PunctuationIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("report\u00A0space")
data class NonbreakingSpaceIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("report-ok")
data class HyphenIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("café")
data class AccentedIdentifierView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}
