package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.ReaderRelationshipBookTable.Companion.READER_RELATIONSHIP_BOOK
import com.siimkinks.sqlitemagic.ReaderRelationshipBooks
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Updated
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import org.junit.Rule
import org.junit.Test

internal class GeneratedRelationshipIdPersistTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun persistingAnExistingRowStoresTheGeneratedIdOfItsNewImmutableRelationship() {
    data class PersistCase(
      val label: String,
      val persist: (ReaderRelationshipBook) -> Any,
      val expectedResult: Any
    )

    listOf(
      PersistCase(
        label = "persist",
        persist = { book ->
          book
            .persist()
            .execute()
        },
        expectedResult = Updated
      ),
      PersistCase(
        label = "null-omitting persist",
        persist = { book ->
          book
            .persist()
            .ignoreNullValues()
            .execute()
        },
        expectedResult = Updated
      ),
      PersistCase(
        label = "bulk persist",
        persist = { book ->
          ReaderRelationshipBooks
            .persist(listOf(book))
            .execute()
        },
        expectedResult = true
      )
    ).forEach { case ->
      val bookId = insertedRowId(
        result = ReaderRelationshipBook(
          id = 0L,
          author = ReaderRelationshipAuthor(
            id = 0L,
            name = "Ada"
          )
        )
          .insert()
          .execute(),
        modelName = "ReaderRelationshipBook"
      )

      val result = case.persist(
        ReaderRelationshipBook(
          id = bookId,
          author = ReaderRelationshipAuthor(
            id = 0L,
            name = "${case.label} author"
          )
        )
      )
      val storedAuthor = SELECT
        .FROM(READER_RELATIONSHIP_BOOK)
        .execute()
        .single { it.id == bookId }
        .author
        .name

      assertWithMessage(case.label)
        .that(result to storedAuthor)
        .isEqualTo(case.expectedResult to "${case.label} author")
    }
  }
}
