@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.parameterEncoding

internal data class AuthorSummary(
  val id: Long,
  val name: String
)

internal class AuthorSummaryTable(
  alias: String? = null,
  private val view: GeneratedView = AUTHOR_SUMMARY_VIEW
) : Table<AuthorSummary>(
  name = view.viewName,
  alias = alias,
  generatedView = view
) {
  val AUTHOR_ID = Column(
    table = this,
    name = "author.id",
    nullness = SqlNullable,
    parameterEncoding = LONG.parameterEncoding(),
    selectedRead = SQLiteStatement::getLong
  )
  val NAME = Column(
    table = this,
    name = "name",
    nullness = SqlNullable,
    parameterEncoding = STRING.parameterEncoding(),
    selectedRead = SQLiteStatement::getText
  )

  override val entityFields = listOf(AUTHOR_ID, NAME)

  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = AuthorSummary(
    id = statement.getLong(startIndex),
    name = statement.getText(startIndex + 1)
  )

  override val entityProperties = listOf(
    EntityProperty(
      path = "AuthorSummary.id",
      required = true
    ),
    EntityProperty(
      path = "AuthorSummary.name",
      required = true
    )
  )

  override fun readPartialEntity(
    statement: SQLiteStatement,
    positions: IntArray,
    related: RelatedEntities?
  ) = AuthorSummary(
    id = statement.getLong(positions[0]),
    name = statement.getText(positions[1])
  )

  override infix fun AS(alias: String) = AuthorSummaryTable(
    alias = alias,
    view = view
  )
}

internal val AUTHOR_SUMMARY_VIEW = GeneratedView(
  viewName = "author_summary",
  temporary = false,
  query = {
    SELECT
      .COLUMNS(AUTHOR.ID, AUTHOR.NAME AS "name")
      .FROM(AUTHOR)
      .WHERE(AUTHOR.NAME LIKE "%a%")
  },
  columns = listOf("author.id", "name")
)

internal val AUTHOR_SUMMARY = AuthorSummaryTable()
