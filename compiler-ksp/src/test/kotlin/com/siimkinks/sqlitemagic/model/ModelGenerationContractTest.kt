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
  fun `constructs non-recursive operation builders from shared runtime support`() {
    SqliteMagicCompilation
      .compile(libraryBookSource())
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LibraryBook_Adapter.kt",
        "_LibraryBook.kt"
      )
      .withGeneratedSource("SqliteMagic_LibraryBook_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_LibraryBook_Adapter",
          "EntityDefaultIdentityAdapter<LibraryBook>",
          "EntityIdentityStatementBinder<LibraryBook> by SqliteMagic_LibraryBook_Dao",
          "override fun identity(",
          "override fun hasIdentityValue(",
          "override fun updateStatementSql("
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate("
        )
      }
      .withGeneratedSource("_LibraryBook.kt") { generatedSource ->
        generatedSource.assertContains(
          "InsertBuilder(",
          "UpdateBuilder(",
          "PersistBuilder(",
          "DeleteBuilder(",
          "BulkInsertBuilder(",
          "BulkUpdateBuilder(",
          "BulkPersistBuilder(",
          "BulkDeleteBuilder(",
          "DeleteTableBuilder(",
          "adapter = SqliteMagic_LibraryBook_Adapter",
          "entity = this"
        )
        generatedSource.assertDoesNotContain(
          "entities = listOf(this)"
        )
      }
  }

  @Test
  fun `constructs no-ID operation builders with typed adapter support`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "SharedRuntimeNoId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class SharedRuntimeNoId(
              @Unique val key: String,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_SharedRuntimeNoId_Adapter.kt",
        "_SharedRuntimeNoId.kt"
      )
      .withGeneratedSource("SqliteMagic_SharedRuntimeNoId_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityIdentityAdapter<SharedRuntimeNoId>",
          "Column<*, *, *, SharedRuntimeNoId, NotNullable>",
          "EntityIdentityStatementBinder<SharedRuntimeNoId> by SqliteMagic_SharedRuntimeNoId_Dao",
          "override fun identity(",
          "requireNotNull(entity.key)"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate("
        )
      }
      .withGeneratedSource("_SharedRuntimeNoId.kt") { generatedSource ->
        generatedSource.assertContains(
          "UpdateByColumnBuilder(",
          "PersistByColumnBuilder(",
          "DeleteByColumnBuilder(",
          "BulkUpdateByColumnBuilder(",
          "BulkPersistByColumnBuilder(",
          "BulkDeleteByColumnBuilder(",
          "entity = this"
        )
        generatedSource.assertDoesNotContain(
          "entities = listOf(this)"
        )
      }
  }

  @Test
  fun `generates schema, column metadata, DAO binders and parsers, and CRUD entry points`() {
    SqliteMagicCompilation
      .compile(
        libraryBookSource(),
        kspOptions = mapOf(
          "sqlitemagic.kotlin.public.extensions" to "true"
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LibraryBook_Dao.kt",
        "SqliteMagic_LibraryBook_Adapter.kt",
        "LibraryBookTable.kt",
        "_LibraryBook.kt"
      )
      .withGeneratedSource("SqliteMagic_LibraryBook_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE TABLE IF NOT EXISTS library_books (",
          "book_key TEXT PRIMARY KEY",
          "title_text TEXT DEFAULT 'untitled'",
          "rating REAL DEFAULT 0.0",
          "subtitle TEXT DEFAULT NULL",
          "cover BLOB DEFAULT 0",
          "override val moduleName: String? = null",
          "override val tableName: String = \"library_books\"",
          "override val insertSql: String =",
          "INSERT%s INTO library_books",
          "internal object SqliteMagic_LibraryBook_Adapter",
          "EntityDefaultIdentityAdapter<LibraryBook>",
          "EntityIdentityStatementBinder<LibraryBook> by SqliteMagic_LibraryBook_Dao",
          "override fun identity(",
          "override fun updateStatementSql("
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate("
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
      }
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement",
          "bindNotNullForInsert",
          "bindNotNullForUpdate",
          "generatedRelationshipIds: Map<String, Long>",
          "statement.clearBindings()",
          "values.clear()",
          "SimpleArrayMap<String, Any>",
          "shallowObjectFromCursorPosition",
          "LibraryBook("
        )
      }
      .withGeneratedSource("_LibraryBook.kt") { generatedSource ->
        generatedSource.assertContains(
          "public fun LibraryBook.insert()",
          "fun LibraryBook.insert()",
          "fun LibraryBook.update()",
          "fun LibraryBook.persist()",
          "fun LibraryBook.delete()",
          "object LibraryBooks",
          "fun deleteTable()",
          "fun insert(o: Iterable<LibraryBook>)",
          "fun update(o: Iterable<LibraryBook>)",
          "fun persist(o: Iterable<LibraryBook>)",
          "fun delete(o: Collection<LibraryBook>)",
          "= InsertBuilder(",
          "= UpdateBuilder(",
          "= PersistBuilder(",
          "= DeleteBuilder(",
          "= BulkInsertBuilder(",
          "= BulkUpdateBuilder(",
          "= BulkPersistBuilder(",
          "= BulkDeleteBuilder(",
          "= DeleteTableBuilder(",
          "adapter = SqliteMagic_LibraryBook_Adapter"
        )
      }
  }

  @Test
  fun `generates identity metadata for bulk entity deletion`() {
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
      .assertGeneratedSources("SqliteMagic_BulkDeleteModel_Adapter.kt")
      .withGeneratedSource("SqliteMagic_BulkDeleteModel_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityDefaultIdentityAdapter<BulkDeleteModel>",
          "override fun identity(",
          "override fun updateStatementSql("
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
          "internal fun LibraryBook.insert()",
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
      }
  }

  @Test
  fun `keeps nullable relationship reads warning free`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableIdentityRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableIdentityTarget(
              @Id val id: Long? = null,
              val name: String = ""
            )

            @Table
            data class NullableIdentityOwner(
              @Id(autoIncrement = false) val id: Long,
              val target: NullableIdentityTarget,
              val optionalTarget: NullableIdentityTarget?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableIdentityTarget_Adapter.kt",
        "SqliteMagic_NullableIdentityOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableIdentityTarget_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          """@Suppress("UNCHECKED_CAST")""",
          "override val defaultIdentityColumn: Column<*, *, *, NullableIdentityTarget, NotNullable>"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableIdentityOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "newInstanceWithOnlyId(cursor.getLong(thisTableOffset + 1))",
          "newInstanceWithOnlyId((if (columnIndex2IsNull) null else cursor.getLong(columnIndex2)))"
        )
        generatedSource.assertDoesNotContain(
          "cursor.getLong(thisTableOffset + 1)?.let { it }",
          "cursor.getLong(columnIndex2)?.let { it }",
          "newInstanceWithOnlyId(cursor.getLong(columnIndex1)) ?: throw"
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
        "SqliteMagic_GeneratedIdModel_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "INSERT%s INTO generated_id_model (value) VALUES (?)",
          "UPDATE%s generated_id_model SET value=? WHERE id=?",
          "override fun assignGeneratedId(",
          "entity.id = rowId"
        )
        generatedSource.assertDoesNotContain("INSERT%s INTO generated_id_model (id,")
      }
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement"
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
      }
      .withGeneratedSource("StorageMatrixTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "BooleanColumn<StorageMatrix",
          "BooleanColumn(this, \"enabled\", Utils.INTEGER_PARSER"
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
        "SqliteMagic_InternalRecord_Adapter.kt",
        "_InternalRecord.kt"
      )
      .withGeneratedSource("InternalRecordTable.kt") { generatedSource ->
        generatedSource.assertContains("internal class InternalRecordTable")
      }
      .withGeneratedSource("SqliteMagic_InternalRecord_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("internal object SqliteMagic_InternalRecord_Adapter")
      }
      .withGeneratedSource("_InternalRecord.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal fun InternalRecord.insert()",
          "internal object InternalRecords"
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
