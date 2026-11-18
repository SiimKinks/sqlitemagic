@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.getRequiredLong
import com.siimkinks.sqlitemagic.internal.getRequiredText
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
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding(),
    selectedRead = { statement, index ->
      statement.getRequiredLong(
        index = index,
        nullMessage = AUTHOR_ID_NULL
      )
    }
  )
  val NAME = Column(
    table = this,
    name = "name",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding(),
    selectedRead = { statement, index ->
      statement.getRequiredText(
        index = index,
        nullMessage = NAME_NULL
      )
    }
  )

  override val entityFields = listOf(AUTHOR_ID, NAME)

  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = AuthorSummary(
    id = statement.getRequiredLong(
      index = startIndex,
      nullMessage = AUTHOR_ID_NULL
    ),
    name = statement.getRequiredText(
      index = startIndex + 1,
      nullMessage = NAME_NULL
    )
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
    id = statement.getRequiredLong(
      index = positions[0],
      nullMessage = AUTHOR_ID_NULL
    ),
    name = statement.getRequiredText(
      index = positions[1],
      nullMessage = NAME_NULL
    )
  )

  override infix fun AS(alias: String) = AuthorSummaryTable(
    alias = alias,
    view = view
  )
}

private const val AUTHOR_ID_NULL = """Column "author.id" was NULL"""

private const val NAME_NULL = """Column "name" was NULL"""

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
