package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.NullableLongKeyEntityTable.Companion.NULLABLE_LONG_KEY_ENTITY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.NullableLongKeyEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NegativeOneRowIdInsertTest(
  private val case: Case
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun aRowWithRowIdMinusOneIsReportedAsInserted() {
    val entity = NullableLongKeyEntity(
      id = -1L,
      name = case.label
    )

    val result = case.operation(entity)

    assertThat(result).isEqualTo(case.expected)
    assertThat(
      SELECT
        .FROM(NULLABLE_LONG_KEY_ENTITY)
        .execute()
    )
      .containsExactly(entity)
  }

  class Case(
    val label: String,
    val operation: (NullableLongKeyEntity) -> Any,
    val expected: Any
  ) {
    override fun toString() = label
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun cases() = listOf(
      Case(
        label = "insert",
        operation = { it.insert().execute() },
        expected = EntityInsertResult.Inserted(-1L)
      ),
      Case(
        label = "insert with CONFLICT_IGNORE",
        operation = {
          it
            .insert()
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = EntityInsertResult.Inserted(-1L)
      ),
      Case(
        label = "persist",
        operation = { it.persist().execute() },
        expected = EntityPersistResult.Inserted(-1L)
      ),
      Case(
        label = "persist ignoring null values",
        operation = {
          it
            .persist()
            .ignoreNullValues()
            .execute()
        },
        expected = EntityPersistResult.Inserted(-1L)
      )
    )
  }
}
