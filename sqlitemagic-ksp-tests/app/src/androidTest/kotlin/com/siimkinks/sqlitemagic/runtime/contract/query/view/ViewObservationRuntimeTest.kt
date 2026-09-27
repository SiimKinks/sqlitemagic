package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DependencyOuterViewTable.Companion.A_DEPENDENCY_OUTER
import com.siimkinks.sqlitemagic.PersistentEmbeddedReadbackViewTable.Companion.PERSISTENT_EMBEDDED_READBACK
import com.siimkinks.sqlitemagic.PersistentNestedReadbackViewTable.Companion.PERSISTENT_NESTED_READBACK
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.ReviewRelationshipCompositionViewTable.Companion.REVIEW_RELATIONSHIP_COMPOSITION
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.ViewObservationDeepTable.Companion.VIEW_OBSERVATION_DEEP
import com.siimkinks.sqlitemagic.ViewObservationDependenciesTable.Companion.VIEW_OBSERVATION_DEPENDENCIES
import com.siimkinks.sqlitemagic.ViewObservationOuterTable.Companion.VIEW_OBSERVATION_OUTER
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.view.DependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.PersistentContact
import com.siimkinks.sqlitemagic.fixture.view.PersistentEmbeddedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentInnerReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentNestedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipCompositionView
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDeep
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDeepest
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationDependencies
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationFilter
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationInner
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationJoin
import com.siimkinks.sqlitemagic.fixture.view.ViewObservationOuter
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import com.siimkinks.sqlitemagic.update
import org.junit.Test

private const val NAMED_DATABASE = "view-observation-runtime.db"

class ViewObservationRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun scalarOuterViewObservesItsTransitiveBaseTableAndStopsAfterDisposal() {
    val observer = Select
      .from(A_DEPENDENCY_OUTER)
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val author = QueryCompositionAuthor(
        id = 91L,
        name = "Ada"
      )
      insert(author)
      observer.assertValuesOnly(
        emptyList(),
        listOf(DependencyOuterView(name = "Ada"))
      )

      assertThat(
        author.copy(name = "Grace")
          .update()
          .execute()
      ).isTrue()
      observer.assertValuesOnly(
        emptyList(),
        listOf(DependencyOuterView(name = "Ada")),
        listOf(DependencyOuterView(name = "Grace"))
      )

      observer.dispose()
      insert(
        QueryCompositionAuthor(
          id = 92L,
          name = "Lin"
        )
      )
      observer.assertValueCount(3)
      assertThat(observer.isDisposed).isTrue()
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun scalarViewRefreshesFromItsAuthorTableAndStopsAfterDisposal() {
    val observer = Select
      .from(QUERY_COMPOSITION_AUTHOR_VIEW)
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      insert(
        QueryCompositionAuthor(
          id = 1L,
          name = "Ada"
        )
      )
      observer.assertValuesOnly(
        emptyList(),
        listOf(
          QueryCompositionAuthorView(
            name = "Ada",
            id = 1L
          )
        )
      )

      observer.dispose()
      insert(
        QueryCompositionAuthor(
          id = 2L,
          name = "Grace"
        )
      )
      observer.assertValuesOnly(
        emptyList(),
        listOf(
          QueryCompositionAuthorView(
            name = "Ada",
            id = 1L
          )
        )
      )
      assertThat(observer.isDisposed)
        .isTrue()
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun embeddedAndNestedViewsRefreshFromTheirAuthorTable() {
    val embedded = Select
      .from(PERSISTENT_EMBEDDED_READBACK)
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    val nested = Select
      .from(PERSISTENT_NESTED_READBACK)
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      insert(
        QueryCompositionAuthor(
          id = 7L,
          name = "Ada"
        )
      )
      embedded.assertValuesOnly(
        emptyList(),
        listOf(
          PersistentEmbeddedReadbackView(
            contact = PersistentContact(
              city = "Ada",
              zip = 7
            )
          )
        )
      )
      nested.assertValuesOnly(
        emptyList(),
        listOf(
          PersistentNestedReadbackView(
            inner = PersistentInnerReadbackView(name = "Ada"),
            tail = "Ada"
          )
        )
      )

      embedded.dispose()
      nested.dispose()
      insert(
        QueryCompositionAuthor(
          id = 8L,
          name = "Grace"
        )
      )
      embedded.assertValueCount(2)
      nested.assertValueCount(2)
      assertThat(embedded.isDisposed)
        .isTrue()
      assertThat(nested.isDisposed)
        .isTrue()
    } finally {
      embedded.dispose()
      nested.dispose()
    }
  }

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
    insert(book)
    val insertedBook = Select
      .from(REVIEW_RELATIONSHIP_COMPOSITION)
      .execute()
      .single()
      .book
    val initial = ReviewRelationshipCompositionView(
      book = insertedBook,
      tail = "tail"
    )
    val observer = Select
      .from(REVIEW_RELATIONSHIP_COMPOSITION)
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(listOf(initial))
    try {
      val changedAuthor = insertedBook.author.copy(name = "Grace")
      assertThat(
        changedAuthor
          .update()
          .execute()
      ).isTrue()
      val refreshed = ReviewRelationshipCompositionView(
        book = insertedBook.copy(author = changedAuthor),
        tail = "tail"
      )
      observer.assertValuesOnly(listOf(initial), listOf(refreshed))

      assertThat(
        insertedBook.delete()
          .execute()
      ).isEqualTo(1)
      observer.assertValuesOnly(listOf(initial), listOf(refreshed), emptyList())

      observer.dispose()
      insert(
        ReaderRelationshipBook(
          id = 24L,
          author = changedAuthor
        )
      )
      observer.assertValueCount(3)
      assertThat(observer.isDisposed)
        .isTrue()
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
    val observer = Select
      .from(VIEW_OBSERVATION_DEPENDENCIES)
      .observe()
      .runQuery()
      .test()
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
      assertThat(observer.isDisposed)
        .isTrue()
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
    val observer = Select
      .from(VIEW_OBSERVATION_DEEP)
      .observe()
      .runQuery()
      .test()
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
    val observer = Select
      .from(VIEW_OBSERVATION_OUTER)
      .observe()
      .runQuery()
      .test()
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
        val defaultQuery = Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .compile()
        val namedQuery = Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
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

        val defaultObserver = defaultQuery
          .observe()
          .runQuery()
          .test()
          .assertValuesOnly(listOf(defaultInitial))
        val namedObserver = namedQuery
          .observe()
          .runQuery()
          .test()
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

  private fun insert(value: QueryCompositionAuthor, connection: DbConnection? = null) =
    assertInserted(
      when (connection) {
        null -> value.insert()
          .execute()
        else -> value.insert()
          .usingConnection(connection)
          .execute()
      }
    )

  private fun insert(value: ReaderRelationshipBook) = assertInserted(
    value.insert()
      .execute()
  )

  private fun insert(value: ViewObservationJoin) = assertInserted(
    value.insert()
      .execute()
  )

  private fun insert(value: ViewObservationFilter) = assertInserted(
    value.insert()
      .execute()
  )

  private fun insert(value: ViewObservationDeepest) = assertInserted(
    value.insert()
      .execute()
  )

  private fun assertInserted(result: EntityInsertResult) {
    assertThat(result)
      .isInstanceOf(EntityInsertResult.Inserted::class.java)
  }
}
