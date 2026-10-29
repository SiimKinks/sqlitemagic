package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class ReleaseMigrationPreviewTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  private val projectDirectory get() = temporaryDirectory.toFile()

  private val databaseDirectory get() = projectDirectory.resolve("db")

  private val assetsDirectory get() = projectDirectory.resolve("src/release/assets")

  @Test
  fun `previews the next release without publishing it`() {
    writeChangedSchema()
    val sources = sourceFiles()

    val preview = preview(databaseVersion = 4)

    assertThat(preview.report).isEqualTo(
      """
      Release 5 preview (latest release 4)
      Database version 4 is not above the latest release; set @Database(version = 5) before publishing this release.

      Schema changes since release 4:
        Tables:
          + authors
          ~ books: adds subtitle; drops isbn; changes title; rebuilt
          ~ notes: adds pinned
          ~ tags -> labels: renamed
          - legacy
        Indices:
          + books_title_index
          - books_isbn_index
        Views:
          + book_view
          - old_view

      Warnings:
        Migration drops column(s) of books with their data: isbn. Edit the migration SQL to keep it.
        Migration drops table legacy with its rows. Edit the migration SQL to keep them.

      Migration SQL for src/release/assets/5.sql:
        UPDATE books SET title = '' WHERE title IS NULL;
        DROP INDEX IF EXISTS main."books_isbn_index"
        ALTER TABLE tags RENAME TO labels
        ALTER TABLE books RENAME TO books_
        CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT NOT NULL DEFAULT '', subtitle TEXT)
        INSERT INTO books (id,title) SELECT id,IFNULL(title, '') FROM books_
        DROP TABLE IF EXISTS books_
        ALTER TABLE notes ADD COLUMN pinned INTEGER
        DROP TABLE IF EXISTS legacy
        CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)
        CREATE INDEX IF NOT EXISTS books_title_index ON books (title)
      """.trimIndent()
    )
    assertThat(sourceFiles()).isEqualTo(sources)
  }

  @Test
  fun `previews the SQL that publication writes`() {
    writeChangedSchema()

    val preview = preview(databaseVersion = 4)
    ReleaseMigrationCoordinator.migrate(
      projectDir = projectDirectory,
      databaseDirectory = databaseDirectory,
      variantName = "release",
      databaseVersion = 5
    )

    assertThat(preview.version).isEqualTo(5)
    assertThat(preview.sql).isEqualTo(assetsDirectory.resolve("5.sql").readText())
  }

  @Test
  fun `previews releases from their latest release and declared version`() {
    mapOf(
      "first release" to PreviewCase(
        databaseVersion = 1,
        latestRelease = null,
        current = databaseStructure("books"),
        pendingSql = null,
        report = """
          Release 1 preview (first release)

          First release: generated code creates the schema.

          No warnings.

          No migration SQL.
        """.trimIndent()
      ),
      "declared version above the latest release" to PreviewCase(
        databaseVersion = 9,
        latestRelease = 8 to databaseStructure("books"),
        current = databaseStructure("books"),
        pendingSql = "UPDATE books SET id = id + 100;\n",
        report = """
          Release 9 preview (latest release 8)

          No schema changes since release 8.

          No warnings.

          Migration SQL for src/release/assets/9.sql:
            UPDATE books SET id = id + 100;
        """.trimIndent()
      ),
      "empty migration SQL" to PreviewCase(
        databaseVersion = 9,
        latestRelease = 8 to databaseStructure("books"),
        current = databaseStructure("books"),
        pendingSql = "",
        report = """
          Release 9 preview (latest release 8)

          No schema changes since release 8.

          No warnings.

          No migration SQL.
        """.trimIndent()
      ),
      "declared version of the latest release" to PreviewCase(
        databaseVersion = 8,
        latestRelease = 8 to databaseStructure("books"),
        current = databaseStructure("books", "authors"),
        pendingSql = null,
        report = """
          Release 9 preview (latest release 8)
          Database version 8 is not above the latest release; set @Database(version = 9) before publishing this release.

          Schema changes since release 8:
            Tables:
              + authors

          No warnings.

          Migration SQL for src/release/assets/9.sql:
            CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)
        """.trimIndent()
      )
    ).forEach { (label, case) ->
      projectDirectory
        .listFiles()
        .orEmpty()
        .forEach(File::deleteRecursively)
      DatabaseStructureJson.write(
        file = databaseDirectory.resolve("latest.struct"),
        structure = case.current
      )
      case.latestRelease?.let { (version, structure) ->
        DatabaseStructureJson.write(
          file = databaseDirectory.resolve("releases/$version.struct"),
          structure = structure
        )
      }
      case.pendingSql?.let { sql ->
        writeAsset(
          name = "${case.databaseVersion}.sql",
          text = sql
        )
      }

      assertWithMessage(label)
        .that(preview(databaseVersion = case.databaseVersion).report)
        .isEqualTo(case.report)
    }
  }

  private fun writeChangedSchema() {
    val id = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val tagName = migrationColumn(
      name = "name",
      schema = "name TEXT"
    )
    val noteBody = migrationColumn(
      name = "body",
      schema = "body TEXT"
    )
    DatabaseStructureJson.write(
      file = databaseDirectory.resolve("releases/4.struct"),
      structure = DatabaseStructure(
        tables = listOf(
          migrationTable(
            name = "books",
            columns = listOf(
              id,
              migrationColumn(
                name = "title",
                schema = "title TEXT"
              ),
              migrationColumn(
                name = "isbn",
                schema = "isbn TEXT"
              )
            )
          ),
          migrationTable(
            name = "notes",
            columns = listOf(id, noteBody)
          ),
          migrationTable(
            name = "tags",
            columns = listOf(id, tagName)
          ),
          migrationTable(
            name = "legacy",
            columns = listOf(
              id,
              migrationColumn(
                name = "payload",
                schema = "payload TEXT"
              )
            )
          )
        ).associateBy(TableStructure::name),
        indices = mapOf(
          "books_isbn_index" to IndexStructure(
            name = "books_isbn_index",
            indexSql = "CREATE INDEX IF NOT EXISTS books_isbn_index ON books (isbn)",
            forTable = "books"
          )
        ),
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
      )
    )
    DatabaseStructureJson.write(
      file = databaseDirectory.resolve("latest.struct"),
      structure = DatabaseStructure(
        tables = listOf(
          migrationTable(
            name = "books",
            columns = listOf(
              id,
              migrationColumn(
                name = "title",
                schema = "title TEXT NOT NULL DEFAULT ''",
                notNull = true,
                notNullDefault = "''"
              ),
              migrationColumn(
                name = "subtitle",
                schema = "subtitle TEXT"
              )
            )
          ),
          migrationTable(
            name = "notes",
            columns = listOf(
              id,
              noteBody,
              migrationColumn(
                name = "pinned",
                schema = "pinned INTEGER"
              )
            )
          ),
          migrationTable(
            name = "labels",
            columns = listOf(id, tagName)
          ),
          migrationTable(
            name = "authors",
            columns = listOf(id)
          )
        ).associateBy(TableStructure::name),
        indices = mapOf(
          "books_title_index" to IndexStructure(
            name = "books_title_index",
            indexSql = "CREATE INDEX IF NOT EXISTS books_title_index ON books (title)",
            forTable = "books"
          )
        ),
        views = mapOf("book_view" to ViewStructure(name = "book_view"))
      )
    )
    writeAsset(
      name = "5.sql",
      text = "UPDATE books SET title = '' WHERE title IS NULL;\n"
    )
  }

  private fun preview(databaseVersion: Int) = ReleaseMigrationCoordinator.preview(
    projectDir = projectDirectory,
    databaseDirectory = databaseDirectory,
    variantName = "release",
    databaseVersion = databaseVersion
  )

  private fun sourceFiles() = projectDirectory
    .walkTopDown()
    .filter(File::isFile)
    .associate { it.relativeTo(projectDirectory).invariantSeparatorsPath to it.readText() }

  private fun writeAsset(
    name: String,
    text: String
  ) {
    assetsDirectory.mkdirs()
    assetsDirectory
      .resolve(name)
      .writeText(text)
  }

  private data class PreviewCase(
    val databaseVersion: Int,
    val latestRelease: Pair<Int, DatabaseStructure>?,
    val current: DatabaseStructure,
    val pendingSql: String?,
    val report: String
  )
}
