package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNoWarnings
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelGenerationContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generates String ID cursor readers and internal entry points by default`() {
    compiler
      .compile(libraryBookSource())
      .isOk()
      .withGeneratedSource("_LibraryBook.kt") {
        it.assertContains(
          "internal fun LibraryBook.insert()",
          "internal object LibraryBooks"
        )
      }
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") {
        it.assertContains(
          "fun objectFromCursorPosition(cursor: Cursor, startIndex: Int): LibraryBook",
          "id = cursor.getString(startIndex)",
          "title = cursor.getString(startIndex + 1)",
          "subtitle = if (cursor.isNull(startIndex + 3)) null else cursor.getString(startIndex + 3)",
          "fun newInstanceWithOnlyId(id: String): LibraryBook",
          "bindString"
        )
      }
  }

  @Test
  fun `generates schema, column metadata, DAO binders and parsers, and visibility-aware CRUD entry points`() {
    compiler
      .compile(
        libraryBookSource(),
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
      .withGeneratedSource("SqliteMagic_LibraryBook_Adapter.kt") {
        it.assertContains(
          "CREATE TABLE IF NOT EXISTS library_books (",
          "book_key TEXT NOT NULL PRIMARY KEY, ",
          "title_text TEXT NOT NULL DEFAULT 'untitled', ",
          "rating REAL NOT NULL DEFAULT 0.0, ",
          "subtitle TEXT DEFAULT NULL, ",
          "cover BLOB NOT NULL DEFAULT X'')",
          "override val moduleName: String? = null",
          """override val tableName: String = "library_books"""",
          "override val insertSql: String =",
          "INSERT%s INTO library_books",
          "internal object SqliteMagic_LibraryBook_Adapter",
          "EntityDefaultIdentityAdapter<LibraryBook>",
          "EntityIdentityStatementBinder<LibraryBook> by SqliteMagic_LibraryBook_Dao",
          "override fun identity(",
          "override fun hasIdentityValue(",
          "override fun updateStatementSql("
        )
      }
      .withGeneratedSource("LibraryBookTable.kt") {
        it
          .assertContainsUnwrapped(
            "val LIBRARY_BOOK: LibraryBookTable",
            "val BOOK_KEY: UniqueColumn<LibraryBook, String, Required, String, String, Required, String>",
            "val TITLE_TEXT: Column<LibraryBook, String, Required, String, String, Required, String>",
            "val RATING: Column<LibraryBook, Double, Required, Double, Double, Required, Double>",
            "val SUBTITLE: Column<LibraryBook, String, SqlNullable, String?, String?, SqlNullable, String?>",
            "val COVER: Column<LibraryBook, ByteArray, Required, ByteArray, ByteArray, Required, ByteArray>"
          )
          .assertContains(
            "class LibraryBookTable",
            "Table<LibraryBook>",
            "parameterEncoding = SqlType.STRING.",
            "parameterEncoding = SqlType.DOUBLE.",
            "parameterEncoding = SqlType.BYTE_ARRAY.",
            "nullness = SqlNullable",
            "override infix fun AS("
          )
      }
      .withGeneratedSource("SqliteMagic_LibraryBook_Dao.kt") {
        it.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement",
          "bindNotNullForInsert",
          "bindNotNullForUpdate",
          "generatedRelationshipIds: Map<String, Long>",
          "statement.clearBindings()",
          "values.clear()",
          "MutableScatterMap<String, Any>",
          "objectFromCursorPosition",
          "LibraryBook("
        )
      }
      .withGeneratedSource("_LibraryBook.kt") {
        it.assertContains(
          "public fun LibraryBook.insert()",
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
          "adapter = SqliteMagic_LibraryBook_Adapter",
          "entity = this"
        )
      }
      .withGeneratedSource("InternalRecordTable.kt") {
        it.assertContains("internal class InternalRecordTable")
      }
      .withGeneratedSource("SqliteMagic_InternalRecord_Adapter.kt") {
        it.assertContains("internal object SqliteMagic_InternalRecord_Adapter")
      }
      .withGeneratedSource("_InternalRecord.kt") {
        it.assertContains(
          "internal fun InternalRecord.insert()",
          "internal object InternalRecords"
        )
      }
  }

  @Test
  fun `keeps nullable relationship reads warning free`() {
    compiler
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
      .withGeneratedSource("SqliteMagic_NullableIdentityTarget_Adapter.kt") {
        it.assertContains(
          """override val defaultIdentityColumnName: String = "id""""
        )
      }
      .withGeneratedSource("SqliteMagic_NullableIdentityOwner_Dao.kt") {
        it.assertContains(
          "newInstanceWithOnlyId(cursor.getLong(startIndex + 1))",
          "optionalTarget = if (cursor.isNull(startIndex + 2)) null else"
        )
      }
      .assertNoWarnings("SqliteMagic_NullableIdentityOwner_Dao.kt")
  }

  @Test
  fun `excludes auto-increment IDs from inserts and binds them for updates`() {
    compiler
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
      .withGeneratedSource("SqliteMagic_GeneratedIdModel_Dao.kt") {
        it.assertContains(
          "bindToInsertStatement",
          "bindToUpdateStatement"
        )
      }
  }

  @Test
  fun `guards update null omission only with identity columns`() {
    compiler
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
      .withGeneratedSource("SqliteMagic_UpdateNullOmissionGuards_Dao.kt") {
        it.assertContains(
          "when (columnName)",
          """"id" ->""",
          "else -> {",
          """if (columnName != "identity_value")""",
          "entity.nullableValue?.let {",
          """values.put("nullable_value", it)""",
          "entity.nullableUniqueValue?.let {",
          """values.put("nullable_unique_value", it)""",
          """values.put("regular_value", entity.regularValue)"""
        )
      }
  }

  @Test
  fun `generates storage-aware binders parsers and Boolean columns`() {
    compiler
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
              val nullableEnabled: Boolean?,
              @Unique val uniqueEnabled: Boolean
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_StorageMatrix_Dao.kt",
        "StorageMatrixTable.kt"
      )
      .withGeneratedSource("SqliteMagic_StorageMatrix_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "statement.bindLong(1, entity.id)",
          "statement.bindBlob",
          ".toByteArray()",
          "byteArrayOf",
          "statement.bindDouble",
          "statement.bindString",
          "statement.bindNull",
          "cursor.getBlob",
          "toTypedArray()",
          "cursor.getFloat",
          "enabled = checkNotNull(BooleanTransformer.dbValueToObject(cursor.getInt(startIndex + 10)))",
          "uniqueEnabled = checkNotNull(BooleanTransformer.dbValueToObject",
          "Transformer com.siimkinks.sqlitemagic.transformer.BooleanTransformer.dbValueToObject " +
              "returned null for required property enabled",
          "Transformer com.siimkinks.sqlitemagic.transformer.BooleanTransformer.dbValueToObject " +
              "returned null for required property uniqueEnabled",
          "nullableEnabled = if (cursor.isNull(startIndex + 11)) null else " +
              "BooleanTransformer.dbValueToObject(cursor.getInt(startIndex + 11))"
        )
        generatedSource.assertDoesNotContain("nullableEnabled = checkNotNull")
      }
      .withGeneratedSource("StorageMatrixTable.kt") {
        it
          .assertContainsUnwrapped(
            "val ENABLED: Column<StorageMatrix, Boolean, Required, Boolean, Boolean, Required, Boolean>",
            "val NULLABLE_ENABLED: Column<StorageMatrix, Boolean, SqlNullable, Boolean?, Boolean?, SqlNullable, " +
                "Boolean?>",
            "val UNIQUE_ENABLED: Column<StorageMatrix, Boolean, Required, Boolean, Boolean, Required, Boolean>",
            "val TEXT: Column<StorageMatrix, String, Required, String, String, Required, String>",
            "val NULLABLE_TEXT: Column<StorageMatrix, String, SqlNullable, String?, String?, SqlNullable, String?>",
            "selectedRead = { cursor, index -> cursor.getBlob(index).toTypedArray() }",
            "selectedRead = { cursor, index -> cursor.getInt(index) != 0 }",
            "selectedRead = { cursor, index -> (if (cursor.isNull(index)) null else cursor.getInt(index) != 0) }"
          )
          .assertContains(
            "parameterEncoding = SqlType.BOOLEAN.",
            "parameterEncoding = SqlType.STRING.",
            "parameterEncoding = SqlType.BOXED_BYTE_ARRAY.",
            "nullness = Required",
            "nullness = SqlNullable"
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
