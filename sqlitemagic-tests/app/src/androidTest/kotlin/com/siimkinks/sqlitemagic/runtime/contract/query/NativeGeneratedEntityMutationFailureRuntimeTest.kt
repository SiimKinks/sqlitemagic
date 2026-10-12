package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedIdCallbackEntityTable.Companion.GENERATED_ID_CALLBACK_ENTITY
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthors
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.model.GeneratedIdCallbackEntity
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.namespaceContractWritableDatabase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NativeGeneratedEntityMutationFailureRuntimeTest(
  private val operation: String,
  private val transactionMode: String
) : RuntimeDatabaseTest() {
  @Before
  fun prepareFixture() {
    val database = namespaceContractWritableDatabase()
    database.execSQL("DROP TRIGGER IF EXISTS generated_mutation_fail")
    database.execSQL("INSERT INTO query_composition_author(id,name) VALUES (1,'one'),(2,'two'),(3,'three')")
    if (operation.endsWith("callback")) return
    database.execSQL(
      when (operation) {
        "delete table" -> "CREATE TRIGGER generated_mutation_fail BEFORE DELETE ON query_composition_author " +
            "WHEN OLD.id=2 BEGIN SELECT RAISE(FAIL,'stop'); END"
        else -> "CREATE TRIGGER generated_mutation_fail BEFORE UPDATE ON query_composition_author " +
            "WHEN OLD.id=2 BEGIN UPDATE query_composition_author SET name='retained' WHERE id=1; " +
            "SELECT RAISE(FAIL,'stop'); END"
      }
    )
  }

  @After
  fun removeFixture() {
    namespaceContractWritableDatabase()
      .execSQL("DROP TRIGGER IF EXISTS generated_mutation_fail")
  }

  @Test
  fun retainedGeneratedWriteRefreshesOnlyAfterCommit() {
    if (operation.endsWith("callback")) {
      assertGeneratedIdCallbackFailure()
      return
    }
    val query = Select
      .from(QUERY_COMPOSITION_AUTHOR)
      .orderBy(QUERY_COMPOSITION_AUTHOR.ID.asc())
      .compile()
    val observer = query.observe()
      .runQuery()
      .test()
    val transaction = when (transactionMode) {
      "none" -> null
      else -> SqliteMagic.newTransaction()
    }
    try {
      assertThrows(Exception::class.java) {
        when (operation) {
          "delete table" -> QueryCompositionAuthors
            .deleteTable()
            .execute()
          else -> {
            val entity = QueryCompositionAuthor(
              id = 2L,
              name = "changed"
            )
            when (operation) {
              "null omitting update" -> entity
                .persist()
                .ignoreNullValues()
                .execute()
              else -> entity
                .update()
                .execute()
            }
          }
        }
      }
      if (transaction != null) observer.assertValueCount(1)
      if (transactionMode == "commit") transaction?.markSuccessful()
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
        transactionMode == "rollback" -> original
        operation == "delete table" -> original.drop(1)
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
        "rollback" -> observer.assertValuesOnly(original)
        else -> observer.assertValuesOnly(original, expected)
      }
    } finally {
      observer.dispose()
    }
  }

  private fun assertGeneratedIdCallbackFailure() {
    val query = Select
      .from(GENERATED_ID_CALLBACK_ENTITY)
      .orderBy(GENERATED_ID_CALLBACK_ENTITY.ID.asc())
      .compile()
    val observer = query
      .observe()
      .runQuery()
      .test()
    val entity = GeneratedIdCallbackEntity().apply {
      value = "inserted"
      rejectGeneratedId = true
    }
    val transaction = when (transactionMode) {
      "none" -> null
      else -> SqliteMagic.newTransaction()
    }
    try {
      val failure = assertThrows(Exception::class.java) {
        when (operation) {
          "insert callback" -> entity.insert()
            .execute()
          else -> entity.persist()
            .execute()
        }
      }
      assertThat(failure.cause)
        .hasMessageThat()
        .isEqualTo("Generated ID callback failed")
      assertThat(entity.id)
        .isNull()
      if (transaction != null) observer.assertValueCount(1)
      if (transactionMode == "commit") transaction?.markSuccessful()
    } finally {
      transaction?.end()
    }
    try {
      when (transactionMode) {
        "rollback" -> {
          assertThat(query.execute())
            .isEmpty()
          observer.assertValuesOnly(emptyList())
        }
        else -> {
          val rows = query.execute()
          assertThat(rows)
            .hasSize(1)
          val rowId = rows.single().id
          assertThat(rowId)
            .isNotNull()
          val expected = listOf(GeneratedIdCallbackEntity().apply {
            id = rowId
            value = "inserted"
          })
          assertThat(rows)
            .isEqualTo(expected)
          observer.assertValuesOnly(emptyList(), expected)
        }
      }
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0} transaction={1}")
    fun cases() = listOf(
      "update",
      "null omitting update",
      "delete table",
      "insert callback",
      "persist callback"
    ).flatMap { operation ->
      listOf("none", "commit", "rollback")
        .map { transaction -> arrayOf(operation, transaction) }
    }
  }
}
