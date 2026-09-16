package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.mockSqliteIdentifier
import com.siimkinks.sqlitemagic.index.mockSqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.schema.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.view.mockViewElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class DatabaseStructureTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `stores complete table column and future index state`() {
    val column = ColumnStructure(
      id = true,
      autoIncrement = false,
      name = "book_key",
      onDeleteCascade = false,
      sqlType = "TEXT",
      schema = "book_key TEXT PRIMARY KEY"
    )
    val table = TableStructure(
      name = "books",
      schema = "CREATE TABLE IF NOT EXISTS books (book_key TEXT PRIMARY KEY)",
      columns = arrayListOf(column)
    )
    val index = IndexStructure(
      name = "books_key",
      indexSql = "CREATE UNIQUE INDEX IF NOT EXISTS books_key ON books (book_key)",
      forTable = "books"
    )
    val actual = DatabaseStructure(
      tables = linkedMapOf("books" to table),
      indices = linkedMapOf("books_key" to index),
      temporaryTables = linkedMapOf("book_drafts" to TableStructure(name = "book_drafts")),
      temporaryIndices = linkedMapOf(
        "book_drafts_key" to IndexStructure(
          name = "book_drafts_key",
          indexSql = "CREATE INDEX book_drafts_key ON book_drafts (book_key)",
          forTable = "book_drafts"
        )
      )
    )

    assertThat(actual).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf(
          "books" to TableStructure(
            name = "books",
            schema = "CREATE TABLE IF NOT EXISTS books (book_key TEXT PRIMARY KEY)",
            columns = arrayListOf(
              ColumnStructure(
                id = true,
                autoIncrement = false,
                name = "book_key",
                onDeleteCascade = false,
                sqlType = "TEXT",
                schema = "book_key TEXT PRIMARY KEY"
              )
            )
          )
        ),
        indices = linkedMapOf(
          "books_key" to IndexStructure(
            name = "books_key",
            indexSql = "CREATE UNIQUE INDEX IF NOT EXISTS books_key ON books (book_key)",
            forTable = "books"
          )
        ),
        temporaryTables = linkedMapOf(
          "book_drafts" to TableStructure(name = "book_drafts")
        ),
        temporaryIndices = linkedMapOf(
          "book_drafts_key" to IndexStructure(
            name = "book_drafts_key",
            indexSql = "CREATE INDEX book_drafts_key ON book_drafts (book_key)",
            forTable = "book_drafts"
          )
        )
      )
    )
  }

  @Test
  fun `defaults absent view maps in legacy snapshots`() {
    val structure = DatabaseStructureJson.read(
      """
      {
        "tables": {},
        "indices": {},
        "temporaryTables": {},
        "temporaryIndices": {}
      }
      """.trimIndent()
    )

    assertThat(structure)
      .isEqualTo(DatabaseStructure())
    assertThat(DatabaseStructureJson.write(structure))
      .contains(""""views":{}""")
    assertThat(DatabaseStructureJson.write(structure))
      .contains(""""temporaryViews":{}""")
  }

  @Test
  fun `round trips view names and module ownership`() {
    val structure = DatabaseStructure(
      views = linkedMapOf(
        "Raw.View" to ViewStructure(
          name = "Raw.View",
          moduleName = "Reports"
        )
      ),
      temporaryViews = linkedMapOf(
        "Current.View" to ViewStructure(
          name = "Current.View",
          moduleName = null
        )
      )
    )

    assertThat(
      DatabaseStructureJson.read(
        DatabaseStructureJson.write(structure)
      )
    ).isEqualTo(structure)
  }

  @Test
  fun `constructs views deterministically by declaration order and namespace`() {
    val views = listOf(
      mockView(
        name = "late_view",
        schema = MAIN,
        declarationOrder = 20,
        moduleName = "Reports"
      ),
      mockView(
        name = "temporary_view",
        schema = TEMPORARY,
        declarationOrder = 5,
        moduleName = null
      ),
      mockView(
        name = "early_view",
        schema = MAIN,
        declarationOrder = 1,
        moduleName = null
      )
    )

    val structure = DatabaseStructure.from(
      orderedTables = CreationOrderedTables.from(emptyList()),
      views = views
    )

    assertThat(structure.views.keys.toList())
      .containsExactly("early_view", "late_view")
      .inOrder()
    assertThat(structure.temporaryViews.keys.toList())
      .containsExactly("temporary_view")
    assertThat(structure.views.getValue("late_view")).isEqualTo(
      ViewStructure(
        name = "late_view",
        moduleName = "Reports"
      )
    )
    assertThat(structure.temporaryViews.getValue("temporary_view")).isEqualTo(
      ViewStructure(
        name = "temporary_view",
        moduleName = null
      )
    )
  }

  @Test
  fun `combines structures without mutating either input`() {
    val main = DatabaseStructure(
      tables = linkedMapOf("main" to TableStructure(name = "main")),
      temporaryTables = linkedMapOf("main_temp" to TableStructure(name = "main_temp")),
      views = linkedMapOf(
        "main_view" to ViewStructure(
          name = "main_view",
          moduleName = null
        )
      ),
      temporaryViews = linkedMapOf(
        "main_temp_view" to ViewStructure(
          name = "main_temp_view",
          moduleName = null
        )
      )
    )
    val feature = DatabaseStructure(
      tables = linkedMapOf("feature" to TableStructure(name = "feature")),
      indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index")),
      temporaryIndices = linkedMapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index")),
      views = linkedMapOf(
        "feature_view" to ViewStructure(
          name = "feature_view",
          moduleName = "Feature"
        )
      ),
      temporaryViews = linkedMapOf(
        "feature_temp_view" to ViewStructure(
          name = "feature_temp_view",
          moduleName = "Feature"
        )
      )
    )

    assertThat(main + feature).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf(
          "main" to TableStructure(name = "main"),
          "feature" to TableStructure(name = "feature")
        ),
        indices = linkedMapOf(
          "feature_index" to IndexStructure(name = "feature_index")
        ),
        temporaryTables = linkedMapOf(
          "main_temp" to TableStructure(name = "main_temp")
        ),
        temporaryIndices = linkedMapOf(
          "feature_temp_index" to IndexStructure(name = "feature_temp_index")
        ),
        views = linkedMapOf(
          "main_view" to ViewStructure(
            name = "main_view",
            moduleName = null
          ),
          "feature_view" to ViewStructure(
            name = "feature_view",
            moduleName = "Feature"
          )
        ),
        temporaryViews = linkedMapOf(
          "main_temp_view" to ViewStructure(
            name = "main_temp_view",
            moduleName = null
          ),
          "feature_temp_view" to ViewStructure(
            name = "feature_temp_view",
            moduleName = "Feature"
          )
        )
      )
    )
    assertThat(main).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("main" to TableStructure(name = "main")),
        temporaryTables = linkedMapOf("main_temp" to TableStructure(name = "main_temp")),
        views = linkedMapOf(
          "main_view" to ViewStructure(
            name = "main_view",
            moduleName = null
          )
        ),
        temporaryViews = linkedMapOf(
          "main_temp_view" to ViewStructure(
            name = "main_temp_view",
            moduleName = null
          )
        )
      )
    )
    assertThat(feature).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("feature" to TableStructure(name = "feature")),
        indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index")),
        temporaryIndices = linkedMapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index")),
        views = linkedMapOf(
          "feature_view" to ViewStructure(
            name = "feature_view",
            moduleName = "Feature"
          )
        ),
        temporaryViews = linkedMapOf(
          "feature_temp_view" to ViewStructure(
            name = "feature_temp_view",
            moduleName = "Feature"
          )
        )
      )
    )
  }

  @Test
  fun `persistentOnly strips temporary views`() {
    val structure = DatabaseStructure(
      views = linkedMapOf(
        "persistent_view" to ViewStructure(
          name = "persistent_view",
          moduleName = "Reports"
        )
      ),
      temporaryViews = linkedMapOf(
        "temporary_view" to ViewStructure(
          name = "temporary_view",
          moduleName = "Reports"
        )
      )
    )

    assertThat(structure.persistentOnly()).isEqualTo(
      DatabaseStructure(
        views = linkedMapOf(
          "persistent_view" to ViewStructure(
            name = "persistent_view",
            moduleName = "Reports"
          )
        )
      )
    )
  }

  @Test
  fun `publication comparison ignores temporary changes and detects persistent indexes`() {
    val previousFile = temporaryDirectory.resolve("previous.struct").toFile()
    val currentFile = temporaryDirectory.resolve("current.struct").toFile()
    val previousStructure = DatabaseStructure(
      tables = linkedMapOf(
        "books" to TableStructure(name = "books")
      )
    )
    val temporaryCurrentStructure = previousStructure.copy(
      temporaryTables = linkedMapOf(
        "book_drafts" to TableStructure(name = "book_drafts")
      ),
      temporaryIndices = linkedMapOf(
        "book_drafts_key" to IndexStructure(
          name = "book_drafts_key",
          indexSql = "CREATE INDEX book_drafts_key ON book_drafts (book_key)",
          forTable = "book_drafts"
        )
      )
    )
    DatabaseStructureJson.write(
      file = previousFile,
      structure = previousStructure
    )
    DatabaseStructureJson.write(
      file = currentFile,
      structure = temporaryCurrentStructure
    )

    assertThat(DatabaseStructureJson.read(currentFile))
      .isEqualTo(temporaryCurrentStructure)
    assertThat(
      DatabaseStructurePublication.hasPersistentChanges(
        previousFile = previousFile,
        currentFile = currentFile
      )
    ).isFalse()

    val persistentCurrentStructure = temporaryCurrentStructure.copy(
      indices = linkedMapOf(
        "books_key" to IndexStructure(
          name = "books_key",
          indexSql = "CREATE INDEX books_key ON books (book_key)",
          forTable = "books"
        )
      )
    )
    DatabaseStructureJson.write(
      file = currentFile,
      structure = persistentCurrentStructure
    )

    assertThat(DatabaseStructureJson.read(currentFile))
      .isEqualTo(persistentCurrentStructure)
    assertThat(
      DatabaseStructurePublication.hasPersistentChanges(
        previousFile = previousFile,
        currentFile = currentFile
      )
    ).isTrue()
    assertThat(
      DatabaseStructurePublication
        .hasPersistentObjects(currentFile)
    ).isTrue()
  }

  @Test
  fun `publication comparison detects persistent view changes`() {
    val previousFile = temporaryDirectory.resolve("previous-view.struct").toFile()
    val currentFile = temporaryDirectory.resolve("current-view.struct").toFile()
    val previousStructure = DatabaseStructure(
      views = linkedMapOf(
        "owned_view" to ViewStructure(
          name = "owned_view",
          moduleName = "Reports"
        )
      )
    )
    val temporaryCurrentStructure = previousStructure.copy(
      temporaryViews = linkedMapOf(
        "temporary_view" to ViewStructure(
          name = "temporary_view",
          moduleName = "Reports"
        )
      )
    )
    DatabaseStructureJson.write(
      file = previousFile,
      structure = previousStructure
    )
    DatabaseStructureJson.write(
      file = currentFile,
      structure = temporaryCurrentStructure
    )

    assertThat(
      DatabaseStructurePublication.hasPersistentChanges(
        previousFile = previousFile,
        currentFile = currentFile
      )
    ).isFalse()

    val changedStructure = previousStructure.copy(
      views = linkedMapOf(
        "renamed_view" to ViewStructure(
          name = "renamed_view",
          moduleName = "Reports"
        )
      )
    )
    DatabaseStructureJson.write(
      file = currentFile,
      structure = changedStructure
    )

    assertThat(
      DatabaseStructurePublication.hasPersistentChanges(
        previousFile = previousFile,
        currentFile = currentFile
      )
    ).isTrue()
  }

  private fun mockView(
    name: String,
    schema: SqliteSchema,
    declarationOrder: Int,
    moduleName: String?
  ) = mockViewElement(
    identity = mockSqliteSchemaIdentity(
      schema = schema,
      identifier = mockSqliteIdentifier(rawName = name)
    ),
    declarationOrder = declarationOrder,
    moduleName = moduleName
  )
}
