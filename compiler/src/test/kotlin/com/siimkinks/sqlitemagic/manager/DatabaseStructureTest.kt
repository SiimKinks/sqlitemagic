package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.mockSqliteIdentifier
import com.siimkinks.sqlitemagic.index.mockSqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.view.mockViewElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class DatabaseStructureTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `round trips temporary objects, view names and module ownership`() {
    val structure = DatabaseStructure(
      temporaryTables = mapOf(
        "temporary_items" to TableStructure(name = "temporary_items")
      ),
      temporaryIndices = mapOf(
        "temporary_name_index" to IndexStructure(
          name = "temporary_name_index",
          indexSql = "CREATE INDEX temporary_name_index ON temporary_items (name)",
          forTable = "temporary_items"
        )
      ),
      views = mapOf(
        "Raw.View" to ViewStructure(
          name = "Raw.View",
          moduleName = "Reports"
        )
      ),
      temporaryViews = mapOf(
        "Current.View" to ViewStructure(name = "Current.View")
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

    assertThat(structure.views)
      .containsExactly(
        "early_view",
        ViewStructure(name = "early_view"),
        "late_view",
        ViewStructure(
          name = "late_view",
          moduleName = "Reports"
        )
      )
      .inOrder()
    assertThat(structure.temporaryViews)
      .containsExactly(
        "temporary_view",
        ViewStructure(name = "temporary_view")
      )
  }

  @Test
  fun `combines structures without mutating either input`() {
    val main = DatabaseStructure(
      tables = mapOf("main" to TableStructure(name = "main")),
      temporaryTables = mapOf("main_temp" to TableStructure(name = "main_temp")),
      views = mapOf(
        "main_view" to ViewStructure(name = "main_view")
      ),
      temporaryViews = mapOf(
        "main_temp_view" to ViewStructure(name = "main_temp_view")
      )
    )
    val feature = DatabaseStructure(
      tables = mapOf("feature" to TableStructure(name = "feature")),
      indices = mapOf("feature_index" to IndexStructure(name = "feature_index")),
      temporaryIndices = mapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index")),
      views = mapOf(
        "feature_view" to ViewStructure(
          name = "feature_view",
          moduleName = "Feature"
        )
      ),
      temporaryViews = mapOf(
        "feature_temp_view" to ViewStructure(
          name = "feature_temp_view",
          moduleName = "Feature"
        )
      )
    )

    assertThat(main + feature).isEqualTo(
      DatabaseStructure(
        tables = mapOf(
          "main" to TableStructure(name = "main"),
          "feature" to TableStructure(name = "feature")
        ),
        indices = mapOf(
          "feature_index" to IndexStructure(name = "feature_index")
        ),
        temporaryTables = mapOf(
          "main_temp" to TableStructure(name = "main_temp")
        ),
        temporaryIndices = mapOf(
          "feature_temp_index" to IndexStructure(name = "feature_temp_index")
        ),
        views = mapOf(
          "main_view" to ViewStructure(name = "main_view"),
          "feature_view" to ViewStructure(
            name = "feature_view",
            moduleName = "Feature"
          )
        ),
        temporaryViews = mapOf(
          "main_temp_view" to ViewStructure(name = "main_temp_view"),
          "feature_temp_view" to ViewStructure(
            name = "feature_temp_view",
            moduleName = "Feature"
          )
        )
      )
    )
    assertThat(main).isEqualTo(
      DatabaseStructure(
        tables = mapOf("main" to TableStructure(name = "main")),
        temporaryTables = mapOf("main_temp" to TableStructure(name = "main_temp")),
        views = mapOf(
          "main_view" to ViewStructure(name = "main_view")
        ),
        temporaryViews = mapOf(
          "main_temp_view" to ViewStructure(name = "main_temp_view")
        )
      )
    )
    assertThat(feature).isEqualTo(
      DatabaseStructure(
        tables = mapOf("feature" to TableStructure(name = "feature")),
        indices = mapOf("feature_index" to IndexStructure(name = "feature_index")),
        temporaryIndices = mapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index")),
        views = mapOf(
          "feature_view" to ViewStructure(
            name = "feature_view",
            moduleName = "Feature"
          )
        ),
        temporaryViews = mapOf(
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
      views = mapOf(
        "persistent_view" to ViewStructure(
          name = "persistent_view",
          moduleName = "Reports"
        )
      ),
      temporaryViews = mapOf(
        "temporary_view" to ViewStructure(
          name = "temporary_view",
          moduleName = "Reports"
        )
      )
    )

    assertThat(structure.persistentOnly()).isEqualTo(
      DatabaseStructure(
        views = mapOf(
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
      tables = mapOf(
        "books" to TableStructure(name = "books")
      )
    )
    val temporaryCurrentStructure = previousStructure.copy(
      temporaryTables = mapOf(
        "book_drafts" to TableStructure(name = "book_drafts")
      ),
      temporaryIndices = mapOf(
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
    val previousSnapshot = DatabaseStructurePublication.load(previousFile)
    assertThat(previousSnapshot.hasPersistentChanges(DatabaseStructurePublication.load(currentFile)))
      .isFalse()

    val persistentCurrentStructure = temporaryCurrentStructure.copy(
      indices = mapOf(
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
    val currentSnapshot = DatabaseStructurePublication.load(currentFile)
    assertThat(previousSnapshot.hasPersistentChanges(currentSnapshot))
      .isTrue()
    assertThat(currentSnapshot.hasPersistentObjects)
      .isTrue()
  }

  @Test
  fun `publication comparison detects persistent view changes`() {
    val previousFile = temporaryDirectory.resolve("previous-view.struct").toFile()
    val currentFile = temporaryDirectory.resolve("current-view.struct").toFile()
    val previousStructure = DatabaseStructure(
      views = mapOf(
        "owned_view" to ViewStructure(
          name = "owned_view",
          moduleName = "Reports"
        )
      )
    )
    val temporaryCurrentStructure = previousStructure.copy(
      temporaryViews = mapOf(
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

    val previousSnapshot = DatabaseStructurePublication.load(previousFile)
    assertThat(previousSnapshot.hasPersistentChanges(DatabaseStructurePublication.load(currentFile)))
      .isFalse()

    val changedStructure = previousStructure.copy(
      views = mapOf(
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

    assertThat(previousSnapshot.hasPersistentChanges(DatabaseStructurePublication.load(currentFile)))
      .isTrue()
  }

  @Test
  fun `publication sees view-only persistent structures but ignores temporary-only structures`() {
    val structureFile = temporaryDirectory
      .resolve("view-only.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = DatabaseStructure(
        temporaryViews = mapOf(
          "session_view" to ViewStructure(name = "session_view")
        )
      )
    )
    assertThat(DatabaseStructurePublication.load(structureFile).hasPersistentObjects)
      .isFalse()

    DatabaseStructureJson.write(
      file = structureFile,
      structure = DatabaseStructure(
        views = mapOf(
          "owned_view" to ViewStructure(name = "owned_view")
        )
      )
    )
    assertThat(DatabaseStructurePublication.load(structureFile).hasPersistentObjects)
      .isTrue()
  }

  @Test
  fun `loaded publication retains derived data and exact bytes after source changes`() {
    val structureFile = temporaryDirectory.resolve("original.struct").toFile()
    val emptyFile = temporaryDirectory.resolve("empty.struct").toFile()
    val restoredFile = temporaryDirectory.resolve("restored/original.struct").toFile()
    val originalBytes = """
      {
        "views": { "Z.View": { "name": "different_value" }, "a_view": { "name": "another_value" } },
        "temporaryViews": { "session_view": { "name": "session_view" } }
      }
    """
      .trimIndent()
      .toByteArray(Charsets.UTF_8)
    structureFile.writeBytes(originalBytes)
    DatabaseStructureJson.write(
      file = emptyFile,
      structure = DatabaseStructure()
    )

    val snapshot = DatabaseStructurePublication.load(structureFile)
    val emptySnapshot = DatabaseStructurePublication.load(emptyFile)
    structureFile.writeText("{malformed")

    assertThat(snapshot.hasPersistentObjects)
      .isTrue()
    assertThat(snapshot.hasPersistentChanges(emptySnapshot))
      .isTrue()
    snapshot.writeTo(restoredFile)
    assertThat(restoredFile.readBytes())
      .isEqualTo(originalBytes)
  }

  @Test
  fun `malformed current publication reports its source path`() {
    val structureFile = temporaryDirectory.resolve("malformed.struct").toFile()
    structureFile.writeText("{malformed")

    assertThat(assertThrows<IllegalStateException> { DatabaseStructurePublication.load(structureFile) })
      .hasMessageThat()
      .isEqualTo("Malformed current database structure snapshot ${structureFile.absolutePath}")
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
