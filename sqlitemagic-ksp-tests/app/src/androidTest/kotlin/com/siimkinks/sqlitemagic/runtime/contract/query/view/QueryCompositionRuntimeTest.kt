package com.siimkinks.sqlitemagic.runtime.contract.query.view

import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.testApplication
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

private const val AUTHOR_VIEW_NAME = "query_composition_author_view"

class QueryCompositionRuntimeTest : RuntimeDatabaseTest() {
  private val expectedAuthors = listOf(
    QueryCompositionAuthor(
      id = 7L,
      name = "Ada"
    ),
    QueryCompositionAuthor(
      id = 9L,
      name = "Grace"
    )
  )
  private val expectedViews = listOf(
    QueryCompositionAuthorView(
      name = "Ada",
      id = 7L
    ),
    QueryCompositionAuthorView(
      name = "Grace",
      id = 9L
    )
  )

  @Before
  fun createSqlView() = withManualDatabase { database ->
    database.execSQL("DROP VIEW IF EXISTS $AUTHOR_VIEW_NAME")
    database.execSQL("CREATE VIEW $AUTHOR_VIEW_NAME AS SELECT id, name FROM query_composition_author")
  }

  @After
  fun dropSqlView() = withManualDatabase { database ->
    database.execSQL("DROP VIEW IF EXISTS $AUTHOR_VIEW_NAME")
  }

  @Test
  fun definingQueryColumnOrderMapsViewProperties() {
    seedAuthors()

    assertThat(
      Select.all()
        .from(QUERY_COMPOSITION_AUTHOR_VIEW)
        .orderBy(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_ID.asc())
        .execute()
    ).containsExactlyElementsIn(expectedViews)
      .inOrder()
  }

  @Test
  fun viewAllKeepsScalarTailAfterItsMappedColumns() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val query = Select
      .columns(
        view.all(),
        view.QUERY_COMPOSITION_AUTHOR_NAME AS "tail"
      )
      .from(view)
      .orderBy(view.QUERY_COMPOSITION_AUTHOR_ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedViews)
      .inOrder()
    assertThat(
      columnValues(
        cursor = query.toCursor().execute(),
        columnName = "tail"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun repeatedViewAliasesMapTheRootAndExposeTheJoinedScalar() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val left = view AS "left_author"
    val right = view AS "right_author"
    val query = Select
      .columns(
        left.all(),
        right.all(),
        right.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"
      )
      .from(left)
      .innerJoin(right.on(left.QUERY_COMPOSITION_AUTHOR_NAME IS right.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(left.QUERY_COMPOSITION_AUTHOR_ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedViews)
      .inOrder()
    assertThat(
      columnValues(
        cursor = query.toCursor().execute(),
        columnName = "joined_name"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun implicitSelectStarKeepsJoinedViewColumnsAfterRootColumns() {
    seedAuthors()
    val root = QUERY_COMPOSITION_AUTHOR
    val joinedView = QUERY_COMPOSITION_AUTHOR_VIEW AS "joined_author_view"
    val query = Select
      .all()
      .from(root)
      .innerJoin(joinedView.on(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(root.ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedAuthors)
      .inOrder()
    val joinedViews = query.toCursor()
      .execute()
      .use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              QueryCompositionAuthorView(
                name = cursor.getString(3),
                id = cursor.getLong(2)
              )
            )
          }
        }
      }
    assertThat(joinedViews)
      .containsExactlyElementsIn(expectedViews)
      .inOrder()

    val scalarQuery = Select
      .columns(
        root.all(),
        joinedView.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"
      )
      .from(root)
      .innerJoin(joinedView.on(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(root.ID.asc())
      .compile()
    assertThat(
      columnValues(
        cursor = scalarQuery.toCursor().execute(),
        columnName = "joined_name"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun missingRequiredViewPositionReportsTheColumn() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val failure = assertThrows(SQLException::class.java) {
      Select
        .columns(view.QUERY_COMPOSITION_AUTHOR_NAME)
        .from(view)
        .execute()
    }

    assertThat(failure).hasMessageThat()
      .contains("Selected columns did not contain required column \"query_composition_author.id\"")
  }

  @Test
  fun unrelatedJoinedAuthorIdCannotFillMissingViewId() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val author = QUERY_COMPOSITION_AUTHOR
    val failure = assertThrows(SQLException::class.java) {
      Select
        .columns(
          view.QUERY_COMPOSITION_AUTHOR_NAME,
          author.ID
        )
        .from(view)
        .innerJoin(author.on(view.QUERY_COMPOSITION_AUTHOR_NAME IS author.NAME))
        .execute()
    }

    assertThat(failure).hasMessageThat()
      .contains("Selected columns did not contain required column \"query_composition_author.id\"")
  }

  private fun seedAuthors() = expectedAuthors.forEach(::insertAuthor)

  private fun insertAuthor(author: QueryCompositionAuthor) {
    when (author.insert().execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> error("Author seed insert was ignored")
    }
  }

  private fun columnValues(
    cursor: Cursor,
    columnName: String
  ) = cursor.use { cursor ->
    val columnIndex = cursor.getColumnIndexOrThrow(columnName)
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.getString(columnIndex))
      }
    }
  }

  private fun withManualDatabase(block: (SQLiteDatabase) -> Unit) = testApplication()
    .openOrCreateDatabase(
      checkNotNull(SqliteMagicDatabase().dbName),
      Context.MODE_PRIVATE,
      null
    )
    .use(block)
}
