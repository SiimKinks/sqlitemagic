package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReviewRelationshipBookViewTable.Companion.REVIEW_RELATIONSHIP_BOOK_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.ViewObservationDeepTable.Companion.VIEW_OBSERVATION_DEEP
import com.siimkinks.sqlitemagic.ViewObservationDependenciesTable.Companion.VIEW_OBSERVATION_DEPENDENCIES
import com.siimkinks.sqlitemagic.ViewObservationOuterTable.Companion.VIEW_OBSERVATION_OUTER
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipBookView
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDeep
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDeepest
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDependencies
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationFilter
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationInner
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationJoin
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationOuter
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val NAMED_DATABASE = "view-observation-runtime.db"

@RunWith(Parameterized::class)
internal class ViewObservationRuntimeTest(
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun relationshipViewRefreshesFromEachDefiningTableAndStopsAfterDisposal() {
    val author = ReaderRelationshipAuthor(
      id = 17L,
      name = "Ada"
    )
    val book = ReaderRelationshipBook(
      id = 23L,
      author = author
    )
    val bookId = insertedRowId(
      result = book
        .insert()
        .execute(),
      modelName = "ReaderRelationshipBook"
    )
    val authorId = SELECT
      .COLUMN(READER_RELATIONSHIP_AUTHOR.ID)
      .FROM(READER_RELATIONSHIP_AUTHOR)
      .execute()
      .single()
    val insertedBook = book.copy(
      id = bookId,
      author = author.copy(id = authorId)
    )
    val initial = ReviewRelationshipBookView(book = insertedBook)
    val observer = adapter
      .runQuery(SELECT.FROM(REVIEW_RELATIONSHIP_BOOK_VIEW))
      .record()
      .assertValuesOnly(listOf(initial))
    try {
      val changedAuthor = insertedBook.author.copy(name = "Grace")
      assertThat(
        changedAuthor
          .update()
          .execute()
      ).isTrue()
      val refreshed = ReviewRelationshipBookView(book = insertedBook.copy(author = changedAuthor))
      observer.assertValuesOnly(listOf(initial), listOf(refreshed))

      assertThat(
        insertedBook.delete()
          .execute()
      ).isEqualTo(1)
      observer.assertValuesOnly(listOf(initial), listOf(refreshed), emptyList())

      observer.dispose()
      insertedResult(
        result = ReaderRelationshipBook(
          id = 24L,
          author = changedAuthor
        )
          .insert()
          .execute(),
        modelName = "ReaderRelationshipBook"
      )
      observer.assertValueCount(3)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun unmappedJoinAndSubqueryTablesBothRefreshTheView() {
    insert(
      QueryCompositionAuthor(
        id = 1L,
        name = "Ada"
      )
    )
    insert(
      ViewObservationJoin(
        id = 1L,
        authorId = 1L
      )
    )
    val filter = ViewObservationFilter(
      id = 1L,
      name = "Ada"
    )
    insert(filter)
    val row = ViewObservationDependencies(
      id = 1L,
      name = "Ada"
    )
    val observer = adapter
      .runQuery(SELECT.FROM(VIEW_OBSERVATION_DEPENDENCIES))
      .record()
      .assertValuesOnly(listOf(row))
    try {
      insert(
        ViewObservationJoin(
          id = 2L,
          authorId = 1L
        )
      )
      observer.assertValuesOnly(listOf(row), listOf(row, row))

      assertThat(
        filter.copy(name = "Grace")
          .update()
          .execute()
      ).isTrue()
      observer.assertValuesOnly(listOf(row), listOf(row, row), emptyList())

      assertThat(
        QueryCompositionAuthor(
          id = 1L,
          name = "Grace"
        )
          .update()
          .execute()
      ).isTrue()
      val changed = ViewObservationDependencies(
        id = 1L,
        name = "Grace"
      )
      observer.assertValuesOnly(listOf(row), listOf(row, row), emptyList(), listOf(changed, changed))

      observer.dispose()
      insert(
        ViewObservationJoin(
          id = 3L,
          authorId = 1L
        )
      )
      observer.assertValueCount(4)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun deepestNestedSubqueryTableRefreshesTheGeneratedView() {
    insert(
      QueryCompositionAuthor(
        id = 1L,
        name = "Ada"
      )
    )
    insert(
      ViewObservationFilter(
        id = 1L,
        name = "Ada"
      )
    )
    val deepest = ViewObservationDeepest(
      id = 1L,
      name = "Ada"
    )
    insert(deepest)
    val initial = ViewObservationDeep(
      id = 1L,
      name = "Ada"
    )
    val observer = adapter
      .runQuery(SELECT.FROM(VIEW_OBSERVATION_DEEP))
      .record()
      .assertValuesOnly(listOf(initial))
    try {
      assertThat(
        deepest.copy(name = "Grace")
          .update()
          .execute()
      ).isTrue()
      observer.assertValuesOnly(listOf(initial), emptyList())
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun nestedDtoIndependentQueryDoesNotBecomeOuterDependency() {
    insert(
      QueryCompositionAuthor(
        id = 1L,
        name = "Ada"
      )
    )
    val initial = ViewObservationOuter(inner = ViewObservationInner(name = "Ada"))
    val observer = adapter
      .runQuery(SELECT.FROM(VIEW_OBSERVATION_OUTER))
      .record()
      .assertValuesOnly(listOf(initial))
    try {
      insert(
        ViewObservationFilter(
          id = 1L,
          name = "unrelated"
        )
      )
      observer.assertValuesOnly(listOf(initial))

      assertThat(
        QueryCompositionAuthor(
          id = 1L,
          name = "Grace"
        )
          .update()
          .execute()
      ).isTrue()
      observer.assertValuesOnly(
        listOf(initial),
        listOf(ViewObservationOuter(inner = ViewObservationInner(name = "Grace")))
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun viewReadsAndObservesTheOuterOperationsConnection() =
    withNamedDatabase(databaseName = NAMED_DATABASE) { application ->
      openNamedConnection(
        application = application,
        databaseName = NAMED_DATABASE,
        database = SqliteMagicDatabase()
      ).use { named ->
        insert(
          QueryCompositionAuthor(
            id = 1L,
            name = "default"
          )
        )
        insert(
          value = QueryCompositionAuthor(
            id = 1L,
            name = "named"
          ),
          connection = named
        )
        val defaultQuery = SELECT
          .FROM(QUERY_COMPOSITION_AUTHOR_VIEW)
          .compile()
        val namedQuery = SELECT
          .FROM(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(named)
          .compile()
        val defaultInitial = QueryCompositionAuthorView(
          name = "default",
          id = 1L
        )
        val namedInitial = QueryCompositionAuthorView(
          name = "named",
          id = 1L
        )
        assertThat(defaultQuery.execute())
          .containsExactly(defaultInitial)
        assertThat(namedQuery.execute())
          .containsExactly(namedInitial)

        val defaultObserver = adapter
          .runQuery(defaultQuery)
          .record()
          .assertValuesOnly(listOf(defaultInitial))
        val namedObserver = adapter
          .runQuery(namedQuery)
          .record()
          .assertValuesOnly(listOf(namedInitial))
        try {
          assertThat(
            QueryCompositionAuthor(
              id = 1L,
              name = "named changed"
            )
              .update()
              .usingConnection(named)
              .execute()
          ).isTrue()
          val namedChanged = QueryCompositionAuthorView(
            name = "named changed",
            id = 1L
          )
          namedObserver.assertValuesOnly(listOf(namedInitial), listOf(namedChanged))
          defaultObserver.assertValuesOnly(listOf(defaultInitial))

          assertThat(
            QueryCompositionAuthor(
              id = 1L,
              name = "default changed"
            )
              .update()
              .execute()
          ).isTrue()
          val defaultChanged = QueryCompositionAuthorView(
            name = "default changed",
            id = 1L
          )
          defaultObserver.assertValuesOnly(listOf(defaultInitial), listOf(defaultChanged))
          namedObserver.assertValuesOnly(listOf(namedInitial), listOf(namedChanged))
        } finally {
          defaultObserver.dispose()
          namedObserver.dispose()
        }
      }
    }

  private fun insert(
    value: QueryCompositionAuthor,
    connection: DbConnection = SqliteMagic.getDefaultConnection()
  ) = insertedResult(
    result = value
      .insert()
      .usingConnection(connection)
      .execute(),
    modelName = "QueryCompositionAuthor"
  )

  private fun insert(value: ViewObservationJoin) = insertedResult(
    result = value
      .insert()
      .execute(),
    modelName = "ViewObservationJoin"
  )

  private fun insert(value: ViewObservationFilter) = insertedResult(
    result = value
      .insert()
      .execute(),
    modelName = "ViewObservationFilter"
  )

  private fun insert(value: ViewObservationDeepest) = insertedResult(
    result = value
      .insert()
      .execute(),
    modelName = "ViewObservationDeepest"
  )

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun adapters() = reactiveAdapters
  }
}
