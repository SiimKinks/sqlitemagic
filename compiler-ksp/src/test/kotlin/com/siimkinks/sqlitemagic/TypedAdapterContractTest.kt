package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class TypedAdapterContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `non-recursive tables emit one composed adapter with shared orchestration`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AdapterBook.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class AdapterBook(
              @Id val id: Long,
              val title: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AdapterBook_Adapter.kt",
        "_AdapterBook.kt"
      )
      .withGeneratedSource("SqliteMagic_AdapterBook_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_AdapterBook_Adapter",
          "EntityDefaultIdentityAdapter<AdapterBook>",
          "EntityIdentityStatementBinder<AdapterBook> by SqliteMagic_AdapterBook_Dao",
          "override fun identity(entity",
          "override val tableName: String = \"adapter_book\"",
          "override val insertSql: String = \"INSERT%s INTO adapter_book",
          "TABLE_SCHEMA"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate("
        )
      }
      .withGeneratedSource("_AdapterBook.kt") { generatedSource ->
        generatedSource.assertContains(
          "InsertBuilder(",
          "UpdateBuilder(",
          "PersistBuilder(",
          "DeleteBuilder(",
          "adapter = SqliteMagic_AdapterBook_Adapter"
        )
      }
  }

  @Test
  fun `insert-only tables omit identity hooks and identity extensions`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AdapterInsertOnly.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class AdapterInsertOnly(
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AdapterInsertOnly_Adapter.kt",
        "_AdapterInsertOnly.kt"
      )
      .withGeneratedSource("SqliteMagic_AdapterInsertOnly_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_AdapterInsertOnly_Adapter",
          "EntityAdapter<AdapterInsertOnly",
          "EntityStatementBinder<AdapterInsertOnly> by SqliteMagic_AdapterInsertOnly_Dao"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "bindToUpdateStatement",
          "bindNotNullForInsert",
          "bindNotNullForUpdate",
          "identity(",
          "defaultIdentityColumn"
        )
      }
      .withGeneratedSource("_AdapterInsertOnly.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun AdapterInsertOnly.insert()",
          "fun insert(o: Iterable<AdapterInsertOnly>)",
          "fun deleteTable()"
        )
        generatedSource.assertDoesNotContain(
          "fun AdapterInsertOnly.update()",
          "fun AdapterInsertOnly.persist()",
          "fun AdapterInsertOnly.delete()",
          "fun update(o:",
          "fun persist(o:",
          "fun delete(o:"
        )
      }
  }

  @Test
  fun `recursive adapters coordinate a related non-recursive adapter`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AdapterRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class AdapterTarget(
              @Id val id: String,
              val value: String = ""
            )

            @Table
            data class AdapterOwner(
              @Id val id: String,
              @Column(handleRecursively = true) val target: AdapterTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AdapterTarget_Adapter.kt",
        "SqliteMagic_AdapterOwner_Adapter.kt",
        "AdapterOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_AdapterOwner_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityRecursiveAdapter<AdapterOwner",
          "operations.insert(",
          "adapter = SqliteMagic_AdapterTarget_Adapter",
          "entity = entity.target"
        )
      }
      .withGeneratedSource("AdapterOwnerTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun addDeepQueryParts(",
          "internal fun addDeepQueryPartsInternal("
        )
      }
  }
}
