package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class OngoingQueryObservationTest : RuntimeDatabaseTest() {
  @Test
  fun relevantInsertEmitsInitialAndRefreshedList() {
    val observer = observeRootQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val expected = insertSimpleMutableEntity(sequence = 1)

      observer.assertValuesOnly(
        emptyList(),
        listOf(expected)
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun irrelevantInsertDoesNotEmit() {
    val observer = observeRootQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      QueryPredicateCatalog.seed()

      observer.assertValuesOnly(emptyList())
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun sqlExecutionErrorIsDelivered() {
    Select
      .raw("SELECT * FROM missing")
      .from(SIMPLE_MUTABLE_ENTITY)
      .observe()
      .runQuery()
      .test()
      .assertFailure(::isMissingTableError)
  }

  @Test
  fun disposalStopsFurtherEmissions() {
    val observer = observeRootQuery()
      .test()
      .assertValuesOnly(emptyList())

    observer.dispose()

    insertSimpleMutableEntity(sequence = 2)

    observer.assertValuesOnly(emptyList())
    assertThat(observer.isDisposed).isTrue()
  }

  private fun observeRootQuery() = Select
    .from(SIMPLE_MUTABLE_ENTITY)
    .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
    .observe()
    .runQuery()

  private fun newSimpleMutableEntity(sequence: Int) = SimpleMutableEntity(
    id = null,
    value = "ongoing-simple-mutable-$sequence",
    boxedBoolean = null,
    primitiveBoolean = true
  )

  private fun insertSimpleMutableEntity(sequence: Int) = newSimpleMutableEntity(sequence)
    .also(::assertSuccessfulInsert)

  private fun isMissingTableError(error: Throwable) = error.message?.contains("no such table: missing") == true

  private fun assertSuccessfulInsert(value: SimpleMutableEntity) =
    when (value.insert().execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> error("Deterministic insert was ignored")
    }
}
