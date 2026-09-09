package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
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
  fun `combines structures without mutating either input`() {
    val main = DatabaseStructure(
      tables = linkedMapOf("main" to TableStructure(name = "main")),
      temporaryTables = linkedMapOf("main_temp" to TableStructure(name = "main_temp"))
    )
    val feature = DatabaseStructure(
      tables = linkedMapOf("feature" to TableStructure(name = "feature")),
      indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index")),
      temporaryIndices = linkedMapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index"))
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
        )
      )
    )
    assertThat(main).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("main" to TableStructure(name = "main")),
        temporaryTables = linkedMapOf("main_temp" to TableStructure(name = "main_temp"))
      )
    )
    assertThat(feature).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("feature" to TableStructure(name = "feature")),
        indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index")),
        temporaryIndices = linkedMapOf("feature_temp_index" to IndexStructure(name = "feature_temp_index"))
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
}
