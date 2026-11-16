package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ModelRowIdContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `renders the row ID as the primary key and the ID as unique`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_Note_Adapter.kt") {
        it.assertContains(
          """CREATE TABLE IF NOT EXISTS \"note\" (\"id\" TEXT NOT NULL UNIQUE, \"row_id\" INTEGER PRIMARY KEY, """ +
              """\"title\" TEXT NOT NULL DEFAULT '')"""
        )
      }
      .withGeneratedSource("SqliteMagic_NullableIdNote_Adapter.kt") {
        it.assertContains("""(\"id\" TEXT UNIQUE, \"rid\" INTEGER PRIMARY KEY, """)
      }
      .withGeneratedSource("SqliteMagic_RealIdNote_Adapter.kt") {
        it.assertContains("""(\"id\" REAL NOT NULL UNIQUE, \"row_id\" INTEGER PRIMARY KEY, """)
      }
      .withGeneratedSource("SqliteMagic_TransformedIdNote_Adapter.kt") {
        it.assertContains("""(\"id\" TEXT NOT NULL UNIQUE, \"row_id\" INTEGER PRIMARY KEY, """)
      }
      .withGeneratedSource("SqliteMagic_Attachment_Adapter.kt") {
        it.assertContains("""\"note\" TEXT NOT NULL REFERENCES \"note\"(\"id\") ON DELETE CASCADE""")
      }
  }

  @Test
  fun `leaves the row ID out of inserts and updates`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_Note_Adapter.kt") {
        it.assertContains(
          """insertSql: String = "INSERT%s INTO \"note\" (\"id\", \"title\") VALUES (?, ?)"""",
          """"UPDATE%s \"note\" SET \"title\"=? WHERE \"id\"=?""""
        )
      }
      .withGeneratedSource("SqliteMagic_Note_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "statement.bindText(1, entity.id)\n    statement.bindText(2, entity.title)\n  }",
          "statement.bindText(1, entity.title)\n    statement.bindText(2, entity.id)\n  }",
          "values.clear()\n    values.put(\"title\", entity.title)\n  }"
        )
        generatedSource.assertDoesNotContain("entity.rowId)")
      }
  }

  @Test
  fun `assigns the row ID back only to a mutable property`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_Note_Adapter.kt") {
        it.assertContains(
          "EntityGeneratedIdAdapter<Note>",
          "override fun assignGeneratedId(entity: Note, rowId: Long) {\n    entity.rowId = rowId\n  }"
        )
      }
      .withGeneratedSource("SqliteMagic_RealIdNote_Adapter.kt") {
        it.assertDoesNotContain("EntityGeneratedIdAdapter", "assignGeneratedId")
      }
  }

  @Test
  fun `selects the row ID as a non-null value`() {
    GENERATED
      .isOk()
      .withGeneratedSource("NoteTable.kt") {
        it.assertContains("val ROW_ID: Column<Note, Long, Required, Long, Long?, SqlNullable, Long?> = Column(")
      }
  }

  @Test
  fun `persists a property marked only with @RowId when persistAll is false`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "ExplicitNote.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.RowId
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            data class ExplicitNote(
              @Id val id: String,
              @RowId val rowId: Long? = null
            )
          """
        )
      )
      .isOk()
      .withGeneratedSource("SqliteMagic_ExplicitNote_Adapter.kt") {
        it.assertContains("""(\"id\" TEXT NOT NULL UNIQUE, \"row_id\" INTEGER PRIMARY KEY)""")
      }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidModels")
  fun `rejects invalid row IDs`(
    label: String,
    declaration: String,
    diagnostic: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "InvalidRowId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.RowId
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption
            import com.siimkinks.sqlitemagic.annotation.Unique

            data class Details(@RowId val rowId: Long? = null, val text: String)

            $declaration
          """
        )
      )
      .assertCompilationError(diagnostic)
  }

  companion object {
    private const val NOT_INTEGER_ID = "@RowId requires an @Id that is not stored as INTEGER"
    private const val CONFLICTING = "@RowId cannot be combined with @Id, @Unique, @Index, @IgnoreColumn, or @Column " +
        "defaultValue or belongsToIndex: InvalidRowId.rowId"

    @JvmStatic
    fun invalidModels() = listOf(
      Arguments.of(
        "INTEGER ID",
        "@Table data class InvalidRowId(@Id val id: Long? = null, @RowId val rowId: Long? = null)",
        "$NOT_INTEGER_ID; InvalidRowId.id already is the row ID: InvalidRowId.rowId"
      ),
      Arguments.of(
        "Boolean ID",
        "@Table data class InvalidRowId(@Id val id: Boolean, @RowId val rowId: Long? = null)",
        "$NOT_INTEGER_ID; InvalidRowId.id already is the row ID: InvalidRowId.rowId"
      ),
      Arguments.of(
        "no ID",
        "@Table data class InvalidRowId(@RowId val rowId: Long? = null, val title: String)",
        "$NOT_INTEGER_ID; a table without an @Id declares its row ID as `@Id val id: Long? = null`: " +
            "InvalidRowId.rowId"
      ),
      Arguments.of(
        "WITHOUT_ROWID table",
        "@Table(options = [TableOption.WITHOUT_ROWID]) " +
            "data class InvalidRowId(@Id val id: String, @RowId val rowId: Long? = null)",
        "@RowId is not allowed in WITHOUT_ROWID or TEMPORARY tables: InvalidRowId.rowId"
      ),
      Arguments.of(
        "TEMPORARY table",
        "@Table(options = [TableOption.TEMPORARY]) " +
            "data class InvalidRowId(@Id val id: String, @RowId val rowId: Long? = null)",
        "@RowId is not allowed in WITHOUT_ROWID or TEMPORARY tables: InvalidRowId.rowId"
      ),
      Arguments.of(
        "two row IDs",
        "@Table data class InvalidRowId(@Id val id: String, @RowId val rowId: Long? = null, " +
            "@RowId val other: Long? = null)",
        "Table must declare at most one @RowId: InvalidRowId.rowId, InvalidRowId.other"
      ),
      Arguments.of(
        "non-null Long",
        "@Table data class InvalidRowId(@Id val id: String, @RowId val rowId: Long = 0)",
        "@RowId requires a Long? property: InvalidRowId.rowId"
      ),
      Arguments.of(
        "Int",
        "@Table data class InvalidRowId(@Id val id: String, @RowId val rowId: Int? = null)",
        "@RowId requires a Long? property: InvalidRowId.rowId"
      ),
      Arguments.of(
        "String",
        "@Table data class InvalidRowId(@Id val id: String, @RowId val rowId: String? = null)",
        "@RowId requires a Long? property: InvalidRowId.rowId"
      ),
      Arguments.of(
        "with @Id",
        "@Table data class InvalidRowId(@Id @RowId val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "with @Unique",
        "@Table data class InvalidRowId(@Id val id: String, @RowId @Unique val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "with @Index",
        "@Table data class InvalidRowId(@Id val id: String, @RowId @Index val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "with @IgnoreColumn",
        "@Table data class InvalidRowId(@Id val id: String, @RowId @IgnoreColumn val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "with a default value",
        """@Table data class InvalidRowId(@Id val id: String, @RowId @Column(defaultValue = "1") """ +
            "val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "in a composite index",
        """@Table data class InvalidRowId(@Id val id: String, @RowId @Column(belongsToIndex = "pair") """ +
            "val rowId: Long? = null)",
        CONFLICTING
      ),
      Arguments.of(
        "inside an embedded value",
        "@Table data class InvalidRowId(@Id val id: String, @Embedded val details: Details)",
        "@RowId is not allowed inside an embedded value: InvalidRowId.details.rowId"
      ),
      Arguments.of(
        "on an embedded property",
        "@Table data class InvalidRowId(@Id val id: String, @Embedded @RowId val details: Details?)",
        "@Embedded cannot be combined with physical-column annotations: InvalidRowId.details"
      )
    )

    private val GENERATED by lazy {
      SqliteMagicCompiler(::modelProcessingSteps).compile(
        SourceFile.kotlin(
          name = "Note.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.RowId
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            @Table
            data class Note(
              @Id val id: String,
              @RowId var rowId: Long? = null,
              val title: String
            )

            @Table
            data class NullableIdNote(
              @Id val id: String?,
              @RowId @Column("rid") val rowId: Long? = null,
              val title: String
            )

            @Table
            data class RealIdNote(
              @Id val id: Double,
              @RowId val rowId: Long? = null,
              val title: String
            )

            data class NoteKey(val value: String)

            @ObjectToDbValue
            fun noteKeyToString(value: NoteKey): String = value.value

            @DbValueToObject
            fun stringToNoteKey(value: String): NoteKey = NoteKey(value)

            @Table
            data class TransformedIdNote(
              @Id val id: NoteKey,
              @RowId val rowId: Long? = null,
              val title: String
            )

            @Table
            data class Attachment(
              @Id val id: Long? = null,
              @Column(onDeleteCascade = true) val note: Note
            )
          """
        )
      )
    }
  }
}
