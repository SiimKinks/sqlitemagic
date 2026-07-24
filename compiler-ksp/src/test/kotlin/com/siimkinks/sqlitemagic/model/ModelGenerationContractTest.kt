package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelGenerationContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `generates schema, column metadata, DAO binders and parsers, and CRUD entry points`() {
    SqliteMagicCompilation
      .compile(
        libraryBookSource(),
        kspOptions = mapOf(
          "sqlitemagic.kotlin.public.extensions" to "true",
          "sqlitemagic.generate.logging" to "true"
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LibraryBook_Dao.kt",
        "SqliteMagic_LibraryBook_Handler.kt",
        "LibraryBookTable.kt",
        "_LibraryBook.kt"
      )
      .withGeneratedSource("SqliteMagic_LibraryBook_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE TABLE IF NOT EXISTS library_books (",
          "book_key TEXT PRIMARY KEY",
          "title_text TEXT DEFAULT 'untitled'",
          "rating REAL DEFAULT 0.0",
          "subtitle TEXT DEFAULT NULL",
          "cover BLOB DEFAULT 0",
          "INSERT_SQL",
          "UPDATE_SQL",
          "InsertBuilder",
          "BulkInsertBuilder",
          "UpdateBuilder",
          "BulkUpdateBuilder",
          "PersistBuilder",
          "BulkPersistBuilder",
          "EntityPersistResult",
          "EntityPersistResult.Inserted",
          "rowId",
          "EntityPersistResult.Updated",
          "EntityPersistResult.Ignored",
          "Single<EntityPersistResult>",
          "DeleteBuilder",
          "BulkDeleteBuilder",
          "DeleteTableBuilder",
          "LogUtil.logDebug"
        )
      }
      .withGeneratedSource("LibraryBookTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "class LibraryBookTable",
          "Table<LibraryBook>",
          "UniqueColumn<",
          "NumericColumn<",
          "Column<",
          "BOOK_KEY",
          "TITLE_TEXT",
          "RATING",
          "SUBTITLE",
          "COVER",
          "override fun `as`(",
          "Query.Mapper<LibraryBook>",
          "shallowObjectFromCursorPosition"
        )
      }
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement",
          "getId",
          "shallowObjectFromCursorPosition",
          "LibraryBook("
        )
      }
      .withGeneratedSource("_LibraryBook.kt") { generatedSource ->
        generatedSource.assertContains(
          "public inline fun LibraryBook.insert()",
          "fun LibraryBook.insert()",
          "fun LibraryBook.update()",
          "fun LibraryBook.persist()",
          "fun LibraryBook.delete()",
          "object LibraryBooks",
          "fun deleteTable()",
          "fun insert(o: Iterable<LibraryBook>)",
          "fun update(o: Iterable<LibraryBook>)",
          "fun persist(o: Iterable<LibraryBook>)",
          "fun delete(o: Collection<LibraryBook>)"
        )
      }
  }

  @Test
  fun `generates internal extension entry points when public extensions are disabled`() {
    SqliteMagicCompilation
      .compile(
        libraryBookSource(),
        kspOptions = mapOf("sqlitemagic.kotlin.public.extensions" to "false")
      )
      .isOk()
      .assertGeneratedSources("_LibraryBook.kt")
      .withGeneratedSource("_LibraryBook.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal inline fun LibraryBook.insert()",
          "internal object LibraryBooks"
        )
      }
  }

  @Test
  fun `preserves String ID types throughout generated declarations`() {
    SqliteMagicCompilation
      .compile(libraryBookSource())
      .isOk()
      .assertGeneratedSources("SqliteMagic_LibraryBook_Dao.kt")
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun getId(entity: LibraryBook): String",
          "fun newInstanceWithOnlyId(id: String): LibraryBook",
          "bindString"
        )
        generatedSource.assertDoesNotContain(
          "fun getId(entity: LibraryBook): Long",
          "Long.toString"
        )
      }
  }

  @Test
  fun `excludes auto-increment IDs from inserts and binds them for updates`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "GeneratedIdModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class GeneratedIdModel(
              @Id var id: Long = 0,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_GeneratedIdModel_Dao.kt",
        "SqliteMagic_GeneratedIdModel_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "INSERT%s INTO generated_id_model (value) VALUES (?)",
          "UPDATE%s generated_id_model SET value=? WHERE id=?"
        )
        generatedSource.assertDoesNotContain("INSERT%s INTO generated_id_model (id,")
      }
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun setId(entity: GeneratedIdModel, id: Long)",
          "entity.id = id",
          "bindToInsertStatement",
          "bindToUpdateStatement"
        )
      }
  }

  private fun libraryBookSource() = SourceFile.kotlin(
    name = "LibraryBook.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Column
      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table("library_books")
      data class LibraryBook(
        @Id
        @Column("book_key")
        val id: String,
        @Column(
          value = "title_text",
          defaultValue = "'untitled'"
        )
        val title: String = "",
        val rating: Double = 0.0,
        val subtitle: String? = null,
        val cover: ByteArray = byteArrayOf()
      )
    """
  )
}
