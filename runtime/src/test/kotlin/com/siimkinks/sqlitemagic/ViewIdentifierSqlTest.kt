package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Test

internal class ViewIdentifierSqlTest {
  @Test
  fun `generated view names render as quoted identifiers`() {
    val cases = listOf(
      "dotted" to "report.v1",
      "spaced" to "report space",
      "embedded quote" to """report"quote""",
      "keyword" to "select"
    )

    for ((label, rawName) in cases) {
      val view = IdentifierViewTable(
        GeneratedView(
          viewName = rawName,
          temporary = false,
          query = { SELECT COLUMN (AUTHOR.NAME AS "name") FROM AUTHOR },
          columns = listOf("name")
        )
      )
      val alias = view AS "safe_alias"
      val quotedName = quoteSqlIdentifier(rawName)
      val named = SELECT COLUMN view.NAME FROM view WHERE (view.NAME IS "Ada")
      val aliased = SELECT COLUMN alias.NAME FROM alias

      assertWithMessage(label)
        .that(named.compile().sql)
        .isEqualTo("""SELECT $quotedName."name" FROM "main".$quotedName WHERE $quotedName."name" = ?""")
      assertWithMessage("$label alias")
        .that(aliased.compile().sql)
        .isEqualTo("""SELECT "safe_alias"."name" FROM "main".$quotedName AS "safe_alias"""")
      assertWithMessage("$label dependency")
        .that(
          QueryDependencies
            .Builder()
            .also(view::addDependencies)
            .build()
            .directSources
        )
        .containsExactly(
          SqliteQuerySource(
            schema = MAIN,
            name = rawName,
            kind = VIEW
          )
        )
      libraryDatabase().use { database ->
        database.createViews(view.generatedView)
        val connection = database.dbConnection()
        assertWithMessage("$label rows")
          .that(listOf(named, aliased).map { it.usingConnection(connection).execute() })
          .isEqualTo(listOf(listOf("Ada"), listOf("Ada", "Grace", "Linus")))
      }
    }
  }

  private class IdentifierViewTable(
    val generatedView: GeneratedView,
    alias: String? = null
  ) : Table<String>(
    name = generatedView.viewName,
    alias = alias,
    generatedView = generatedView
  ) {
    val NAME = Column<String, String, SqlNullable, String, String, Required, String>(
      table = this,
      name = "name",
      nullness = SqlNullable,
      parameterEncoding = STRING.parameterEncoding(),
      selectedRead = Cursor::getString
    )

    override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?): String =
      cursor.getString(startIndex)

    override infix fun AS(alias: String) = IdentifierViewTable(
      generatedView = generatedView,
      alias = alias
    )
  }
}
