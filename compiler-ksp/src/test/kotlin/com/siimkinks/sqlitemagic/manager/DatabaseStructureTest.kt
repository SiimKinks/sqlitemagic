package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class DatabaseStructureTest {
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
      indices = linkedMapOf("books_key" to index)
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
        )
      )
    )
  }

  @Test
  fun `combines structures without mutating either input`() {
    val main = DatabaseStructure(
      tables = linkedMapOf("main" to TableStructure(name = "main"))
    )
    val feature = DatabaseStructure(
      tables = linkedMapOf("feature" to TableStructure(name = "feature")),
      indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index"))
    )

    assertThat(main + feature).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf(
          "main" to TableStructure(name = "main"),
          "feature" to TableStructure(name = "feature")
        ),
        indices = linkedMapOf(
          "feature_index" to IndexStructure(name = "feature_index")
        )
      )
    )
    assertThat(main).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("main" to TableStructure(name = "main"))
      )
    )
    assertThat(feature).isEqualTo(
      DatabaseStructure(
        tables = linkedMapOf("feature" to TableStructure(name = "feature")),
        indices = linkedMapOf("feature_index" to IndexStructure(name = "feature_index"))
      )
    )
  }
}
