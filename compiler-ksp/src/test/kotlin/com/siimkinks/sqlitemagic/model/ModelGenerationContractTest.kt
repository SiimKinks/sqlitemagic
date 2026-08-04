package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
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
          "if (ignoreNullValues)",
          "bindNotNull",
          "OperationHelper",
          "VariableArgsOperationHelper",
          "getEntityDbManager(null,",
          "getInsertStatement",
          "getUpdateStatement",
          "return EntityInsertResult.Ignored",
          "is EntityInsertResult.Ignored -> false",
          "EntityPersistResult",
          "EntityPersistResult.Inserted",
          "rowId",
          "return EntityPersistResult.Updated",
          "return EntityPersistResult.Ignored",
          "Single<EntityPersistResult>",
          "DeleteBuilder",
          "BulkDeleteBuilder",
          "DeleteTableBuilder",
          "executeInTransaction transaction@ {",
          "newTransaction()",
          "transaction.markSuccessful()",
          "transaction.end()",
          "Completable.create",
          "emitter::isDisposed",
          "CancellationException",
          "catch (exception: OperationFailedException)",
          "catch (exception: Exception)",
          "Failed to insert \$entity",
          "Failed to persist \$entity",
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
          "checkNotNull(",
          "SqliteMagic_LibraryBook_Dao::shallowObjectFromCursorPosition"
        )
        generatedSource.assertDoesNotContain(
          "queryDeep ->",
          "requireNotNull(",
          "SqliteMagic_LibraryBook_Dao::fullObjectFromCursorPosition"
        )
      }
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement",
          "fun bindNotNull",
          "statement.clearBindings()",
          "values.clear()",
          "SimpleArrayMap<String, Any>",
          "shallowObjectFromCursorPosition",
          "LibraryBook("
        )
        generatedSource.assertDoesNotContain(
          "ContentValues()",
          "checkNotNull(",
          "fullObjectFromCursorPosition",
          "fun getId(",
          "generatedRelationshipIds",
          "val value0 = entity.bookKey",
          "valuesForInsertIgnoringNull",
          "valuesForUpdateIgnoringNull"
        )
        assertThat(generatedSource.split("fun bindToUpdateStatement")).hasSize(2)
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
  fun `batches bulk entity deletion into one identity statement`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "BulkDeleteModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("bulk_delete_models")
            data class BulkDeleteModel(
              @Id(autoIncrement = false) val id: String,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_BulkDeleteModel_Handler.kt")
      .withGeneratedSource("SqliteMagic_BulkDeleteModel_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "val identities = mutableListOf<Pair<String, String>>()",
          "identities += identity(entity, byColumn)",
          "if (identities.isEmpty())",
          "val sql = \"DELETE FROM bulk_delete_models WHERE \" + identities.first().first",
          "identities.joinToString(\",\") { \"?\" }",
          "db().compileStatement(sql).use",
          "identities.forEachIndexed { index, identity ->",
          "statement.bindString(index + 1, identity.second)"
        )
        assertThat(generatedSource.split("db().compileStatement(sql)")).hasSize(2)
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
  fun `parses a selected whole table from its recorded cursor offset`() {
    SqliteMagicCompilation
      .compile(libraryBookSource())
      .isOk()
      .assertGeneratedSources("SqliteMagic_LibraryBook_Dao.kt")
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "val effectiveTableName = tableName ?: \"library_books\"",
          "val thisTableOffset = columns[effectiveTableName]",
          "val columnIndex0 = thisTableOffset ?: columns[\"\"\"\$effectiveTableName.book_key\"\"\"]",
          "val columnIndex1 = thisTableOffset?.plus(1) ?: " +
              "columns[\"\"\"\$effectiveTableName.title_text\"\"\"]"
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
          "fun newInstanceWithOnlyId(id: String): LibraryBook",
          "bindString"
        )
        generatedSource.assertDoesNotContain(
          "fun getId(",
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
          "UPDATE%s generated_id_model SET value=? WHERE id=?",
          "if (rowId != -1L)",
          "entity.id = rowId"
        )
        generatedSource.assertDoesNotContain("INSERT%s INTO generated_id_model (id,")
      }
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement"
        )
        generatedSource.assertDoesNotContain(
          "fun setId(",
          "entity.id = id"
        )
      }
  }

  @Test
  fun `guards update null omission only with identity columns`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UpdateNullOmissionGuards.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class UpdateNullOmissionGuards(
              @Id val id: Long,
              @Unique val identityValue: String,
              val nullableValue: String?,
              val regularValue: String,
              @Unique val nullableUniqueValue: String?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_UpdateNullOmissionGuards_Dao.kt")
      .withGeneratedSource("SqliteMagic_UpdateNullOmissionGuards_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "when (byColumn)",
          "UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.ID ->",
          "UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.IDENTITY_VALUE ->",
          "else -> throw IllegalArgumentException(",
          "if (byColumn != UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.IDENTITY_VALUE)",
          "entity.nullableValue?.let {",
          "values.put(\"nullable_value\", it)",
          "entity.nullableUniqueValue?.let {",
          "values.put(\"nullable_unique_value\", it)",
          "values.put(\"regular_value\", entity.regularValue)"
        )
        generatedSource.assertDoesNotContain(
          "if (value1 != null)",
          "if (value3 != null)",
          "if (byColumn == UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.ID)",
          "if (byColumn == UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.IDENTITY_VALUE)",
          "byColumn != UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.NULLABLE_VALUE",
          "byColumn != UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.REGULAR_VALUE",
          "byColumn != UpdateNullOmissionGuardsTable.UPDATE_NULL_OMISSION_GUARDS.NULLABLE_UNIQUE_VALUE"
        )
      }
  }

  @Test
  fun `generates storage-aware binders parsers and Boolean columns`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "StorageMatrix.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class StorageMatrix(
              @Id(autoIncrement = false) val id: Long,
              val primitiveBytes: ByteArray,
              val boxedBytes: Array<Byte>,
              val byte: Byte,
              val double: Double,
              val float: Float,
              val int: Int,
              val short: Short,
              val text: String,
              val nullableText: String?,
              val enabled: Boolean,
              @Unique val uniqueEnabled: Boolean
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_StorageMatrix_Dao.kt",
        "StorageMatrixTable.kt",
        "UniqueBooleanColumn.kt"
      )
      .withGeneratedSource("SqliteMagic_StorageMatrix_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "statement.bindLong(1, entity.id)",
          "statement.bindBlob",
          ".toByteArray()",
          "byteArrayOf",
          "statement.bindDouble",
          "statement.bindLong",
          "statement.bindString",
          "statement.bindNull",
          "cursor.getBlob",
          "toTypedArray()",
          "cursor.getFloat"
        )
        generatedSource.assertDoesNotContain(
          "checkNotNull(",
          "Utils.toByteArray",
          "val value0 = entity.id",
          "val value1 = entity.id"
        )
      }
      .withGeneratedSource("StorageMatrixTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "BooleanColumn<StorageMatrix",
          "BooleanColumn(this, \"enabled\", Utils.INTEGER_PARSER"
        )
        generatedSource.assertDoesNotContain(
          "BooleanColumn(this, \"enabled\", false"
        )
      }
      .withGeneratedSource("UniqueBooleanColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "class UniqueBooleanColumn",
          "Unique<N>",
          "BooleanTransformer.objectToDbValue",
          "BooleanTransformer.dbValueToObject"
        )
      }
  }

  @Test
  fun `keeps generated APIs internal for an internal model`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InternalRecord.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            internal data class InternalRecord(
              @Id val id: String
            )
          """
        ),
        kspOptions = mapOf("sqlitemagic.kotlin.public.extensions" to "true")
      )
      .isOk()
      .assertGeneratedSources(
        "InternalRecordTable.kt",
        "SqliteMagic_InternalRecord_Handler.kt",
        "_InternalRecord.kt"
      )
      .withGeneratedSource("InternalRecordTable.kt") { generatedSource ->
        generatedSource.assertContains("internal class InternalRecordTable")
      }
      .withGeneratedSource("SqliteMagic_InternalRecord_Handler.kt") { generatedSource ->
        generatedSource.assertContains("internal object SqliteMagic_InternalRecord_Handler")
      }
      .withGeneratedSource("_InternalRecord.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal inline fun InternalRecord.insert()",
          "internal object InternalRecords"
        )
        generatedSource.assertDoesNotContain("public inline fun InternalRecord.insert()")
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
