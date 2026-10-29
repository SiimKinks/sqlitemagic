package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSimpleRows
import com.siimkinks.sqlitemagic.runtime.support.insertSimpleRow
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test

internal class QueryBuilderDeleteTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeRawPredicateDelete() {
    val first = SimpleMutableEntity(
      id = 101L,
      value = "first"
    )
    val second = SimpleMutableEntity(
      id = 102L,
      value = "second"
    )
    insertSimpleRow(value = first)
    insertSimpleRow(value = second)

    val affectedRows = DELETE
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .WHERE("simple_mutable_entity.value = ?".expr(BOOLEAN, "second"))
      .compile()
      .execute()

    assertThat(affectedRows).isEqualTo(1)
    assertSimpleRows(first)
  }

  @Test
  fun executeTypedPredicatePublishesOnlyForDeletedRows() {
    val first = SimpleMutableEntity(
      id = 201L,
      value = "first"
    )
    val second = SimpleMutableEntity(
      id = 202L,
      value = "second"
    )
    insertSimpleRow(value = first)
    insertSimpleRow(value = second)
    val observer = SELECT
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(listOf(first, second))
    val deleteSecond = (DELETE FROM SIMPLE_MUTABLE_ENTITY) WHERE (SIMPLE_MUTABLE_ENTITY.ID IS checkNotNull(second.id))
    try {
      val affectedRows = deleteSecond.compile()
        .execute()

      assertThat(affectedRows).isEqualTo(1)
      observer.assertValuesOnly(
        listOf(first, second),
        listOf(first)
      )
      assertThat(
        deleteSecond.compile()
          .execute()
      ).isEqualTo(0)
      observer.assertValuesOnly(
        listOf(first, second),
        listOf(first)
      )
      assertSimpleRows(first)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun executeTypedWholeTableDelete() {
    insertSimpleRow(
      SimpleMutableEntity(
        id = 301L,
        value = "first"
      )
    )
    insertSimpleRow(
      SimpleMutableEntity(
        id = 302L,
        value = "second"
      )
    )

    val affectedRows = (DELETE FROM SIMPLE_MUTABLE_ENTITY).compile()
      .execute()

    assertThat(affectedRows).isEqualTo(2)
    assertSimpleRows()
  }
}
