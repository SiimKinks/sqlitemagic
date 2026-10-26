package com.siimkinks.sqlitemagic

import android.database.Cursor
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
    selectedRead = Cursor::getLong
  )
  val NAME = Column<AuthorSummary, String, SqlNullable, String, String, Required, String>(
    table = this,
    name = "name",
    nullness = SqlNullable,
    parameterEncoding = STRING.parameterEncoding(),
    selectedRead = Cursor::getString
  )

  override val entityFields = listOf(AUTHOR_ID, NAME)

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = AuthorSummary(
    id = cursor.getLong(startIndex),
    name = cursor.getString(startIndex + 1)
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

  override fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = AuthorSummary(
    id = cursor.getLong(positions[0]),
    name = cursor.getString(positions[1])
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
