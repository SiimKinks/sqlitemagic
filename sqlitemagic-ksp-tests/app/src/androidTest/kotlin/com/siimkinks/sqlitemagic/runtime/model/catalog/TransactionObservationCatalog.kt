package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import io.reactivex.Observable

internal object TransactionObservationCatalog {
  val simple = SimpleRole
  val string = StringRole

  fun matchingValues() = matchingQuery().execute()

  fun observeMatchingQueries() = matchingQuery().observe()

  fun observeMatchingValues(): Observable<List<String?>> = matchingQuery()
    .observe()
    .runQuery()

  object SimpleRole {
    val table = SIMPLE_MUTABLE_ENTITY

    fun newValue(sequence: Int) = SimpleMutableEntity(
      id = null,
      value = valueFor(sequence),
      boxedBoolean = null,
      primitiveBoolean = true
    )

    fun insert(sequence: Int) = insert(newValue(sequence = sequence))

    fun insert(value: SimpleMutableEntity) = value.also {
      assertSeedInserted(
        result = it
          .insert()
          .execute(),
        modelName = "TransactionObservation.SimpleMutableEntity"
      )
    }
  }

  object StringRole {
    val table = STRING_ID_ENTITY

    fun newValue(sequence: Int) = StringIdEntity(
      id = "transaction-id-$sequence",
      value = valueFor(sequence)
    )

    fun insert(sequence: Int) = insert(newValue(sequence = sequence))

    fun insert(value: StringIdEntity) = value.also {
      assertSeedInserted(
        result = it
          .insert()
          .execute(),
        modelName = "TransactionObservation.StringIdEntity"
      )
    }
  }

  private fun valueFor(sequence: Int) = "transaction-value-$sequence"

  private fun matchingQuery() = Select
    .column(simple.table.VALUE)
    .from(simple.table)
    .innerJoin(string.table.on(simple.table.VALUE.`is`(string.table.VALUE)))
    .orderBy(simple.table.ID.asc())
}
