package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthors
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.defaultWritableDatabase
import com.siimkinks.sqlitemagic.rxjava2.observe
import com.siimkinks.sqlitemagic.update
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NativeGeneratedEntityMutationFailureRuntimeTest(
  private val operation: FailingMutation,
  private val transactionMode: MutationTransactionMode
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Before
  fun prepareFixture() {
    val database = defaultWritableDatabase()
    database.execSQL("DROP TRIGGER IF EXISTS generated_mutation_fail")
    database.execSQL("INSERT INTO query_composition_author(id,name) VALUES (1,'one'),(2,'two'),(3,'three')")
    database.execSQL(
      when (operation) {
        FailingMutation.DELETE_TABLE -> "CREATE TRIGGER generated_mutation_fail BEFORE DELETE ON " +
            "query_composition_author WHEN OLD.id=2 BEGIN SELECT RAISE(FAIL,'stop'); END"
        else -> "CREATE TRIGGER generated_mutation_fail BEFORE UPDATE ON query_composition_author " +
            "WHEN OLD.id=2 BEGIN UPDATE query_composition_author SET name='retained' WHERE id=1; " +
            "SELECT RAISE(FAIL,'stop'); END"
      }
    )
  }

  @After
  fun removeFixture() = defaultWritableDatabase()
    .execSQL("DROP TRIGGER IF EXISTS generated_mutation_fail")

  @Test
  fun retainedGeneratedWriteRefreshesOnlyAfterCommit() {
    val query = SELECT
      .FROM(QUERY_COMPOSITION_AUTHOR)
      .ORDER_BY(QUERY_COMPOSITION_AUTHOR.ID.ASC())
      .compile()
    val observer = query.observe()
      .runQuery()
      .test()
    val transaction = when (transactionMode) {
      MutationTransactionMode.NONE -> null
      else -> SqliteMagic.newTransaction()
    }
    try {
      assertThrows(Exception::class.java) {
        val changed = QueryCompositionAuthor(
          id = 2L,
          name = "changed"
        )
        when (operation) {
          FailingMutation.UPDATE -> changed
            .update()
            .execute()
          FailingMutation.NULL_OMITTING_UPDATE -> changed
            .persist()
            .ignoreNullValues()
            .execute()
          FailingMutation.DELETE_TABLE -> QueryCompositionAuthors
            .deleteTable()
            .execute()
        }
      }
      if (transaction != null) observer.assertValueCount(1)
      if (transactionMode == MutationTransactionMode.COMMIT) transaction?.markSuccessful()
    } finally {
      transaction?.end()
    }
    try {
      val original = listOf(
        QueryCompositionAuthor(
          id = 1L,
          name = "one"
        ),
        QueryCompositionAuthor(
          id = 2L,
          name = "two"
        ),
        QueryCompositionAuthor(
          id = 3L,
          name = "three"
        )
      )
      val expected = when {
        transactionMode == MutationTransactionMode.ROLLBACK -> original
        operation == FailingMutation.DELETE_TABLE -> original.drop(1)
        else -> listOf(
          QueryCompositionAuthor(
            id = 1L,
            name = "retained"
          )
        ) + original.drop(1)
      }
      assertThat(query.execute())
        .isEqualTo(expected)
      when (transactionMode) {
        MutationTransactionMode.ROLLBACK -> observer.assertValuesOnly(original)
        else -> observer.assertValuesOnly(original, expected)
      }
    } finally {
      observer.dispose()
    }
  }

  enum class FailingMutation(private val label: String) {
    UPDATE("update"),
    NULL_OMITTING_UPDATE("null omitting update"),
    DELETE_TABLE("delete table");

    override fun toString() = label
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0} transaction={1}")
    fun cases() = FailingMutation.entries.flatMap { operation ->
      MutationTransactionMode.entries.map { arrayOf(operation, it) }
    }
  }
}

enum class MutationTransactionMode(private val label: String) {
  NONE("none"),
  COMMIT("commit"),
  ROLLBACK("rollback");

  override fun toString() = label
}
