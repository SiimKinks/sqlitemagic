package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class ReleaseMigrationCoordinatorTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  private val databaseDirectory get() = temporaryDirectory.resolve("db")

  private val assetsDirectory get() = temporaryDirectory.resolve("src/release/assets")

  @Test
  fun `publishes the first release from current structures`() {
    val current = databaseStructure("books")
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = current
    )

    migrate()

    assertThat(
      readStructure(databaseDirectory.resolve("releases/1.struct"))
    ).isEqualTo(current)
    assertThat(
      assetsDirectory
        .resolve("1.sql")
        .exists()
    ).isFalse()
  }

  @Test
  fun `publishes a subsequent release migration`() {
    val previous = databaseStructure("books")
    val current = databaseStructure("books", "authors")
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = current
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/3.struct"),
      structure = previous
    )

    migrate()

    assertThat(
      readStructure(databaseDirectory.resolve("releases/4.struct"))
    ).isEqualTo(current)
    assertThat(
      assetsDirectory
        .resolve("4.sql")
        .readText()
    ).isEqualTo("CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)${System.lineSeparator()}")
  }

  @Test
  fun `publishes the next snapshot and removes stale migration when schema is unchanged`() {
    val current = databaseStructure("books")
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = current
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/8.struct"),
      structure = current
    )
    val staleMigration = assetsDirectory.resolve("9.sql")
    staleMigration.parent.createDirectories()
    staleMigration.writeText("stale migration")

    migrate()

    assertThat(
      readStructure(databaseDirectory.resolve("releases/9.struct"))
    ).isEqualTo(current)
    assertThat(
      staleMigration
        .exists()
    ).isFalse()
  }

  @Test
  fun `aggregates current submodule structures in filename order`() {
    writeStructure(
      file = databaseDirectory.resolve("z-feature.struct"),
      structure = databaseStructure("feature")
    )
    writeStructure(
      file = databaseDirectory.resolve("a-main.struct"),
      structure = databaseStructure("main")
    )

    migrate()

    val output = readStructure(databaseDirectory.resolve("releases/1.struct"))
    assertThat(output).isEqualTo(databaseStructure("main", "feature"))
    assertThat(output.tables.keys)
      .containsExactly("main", "feature")
      .inOrder()
  }

  @Test
  fun `ignores nonnumeric files when choosing the first release version`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    assetsDirectory.createDirectories()
    assetsDirectory
      .resolve("2.sql")
      .writeText("old")
    assetsDirectory
      .resolve("10.sql")
      .writeText("old")
    assetsDirectory
      .resolve("not-a-version.sql")
      .writeText("ignored")
    assetsDirectory
      .resolve("100.sql.bak")
      .writeText("ignored")

    migrate()

    assertThat(
      databaseDirectory
        .resolve("releases/11.struct")
        .exists()
    ).isTrue()
  }

  @Test
  fun `uses the latest numeric release snapshot before considering assets`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/2.struct"),
      structure = databaseStructure("books")
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/10.struct"),
      structure = databaseStructure("books")
    )
    databaseDirectory
      .resolve("releases/not-a-version.struct")
      .writeText("ignored")
    assetsDirectory.createDirectories()
    assetsDirectory
      .resolve("99.sql")
      .writeText("ignored")
    assetsDirectory
      .resolve("099.sql")
      .writeText("ignored")

    migrate()

    assertThat(
      databaseDirectory
        .resolve("releases/11.struct")
        .exists()
    ).isTrue()
    assertThat(
      databaseDirectory
        .resolve("releases/100.struct")
        .exists()
    ).isFalse()
  }

  @Test
  fun `rejects duplicate numeric release snapshot versions`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/1.struct"),
      structure = databaseStructure("books")
    )
    writeStructure(
      file = databaseDirectory.resolve("releases/01.struct"),
      structure = databaseStructure("books")
    )

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains("Duplicate numeric struct version 1")
  }

  @Test
  fun `rejects duplicate numeric asset versions`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    assetsDirectory.createDirectories()
    assetsDirectory
      .resolve("1.sql")
      .writeText("old")
    assetsDirectory
      .resolve("01.sql")
      .writeText("old")

    assertThat(assertThrows<IllegalStateException>(::migrate))
      .hasMessageThat()
      .contains("Duplicate numeric sql version 1")
  }

  @Test
  fun `fails on a malformed current structure`() {
    databaseDirectory
      .resolve("module.struct")
      .apply {
        parent.createDirectories()
        writeText("not json")
      }

    assertThat(assertThrows<IllegalStateException>(::migrate))
      .hasMessageThat()
      .contains("Malformed current")
    assertThat(
      databaseDirectory
        .resolve("releases/1.struct")
        .exists()
    ).isFalse()
  }

  @Test
  fun `fails on a malformed latest release structure`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    databaseDirectory
      .resolve("releases/7.struct")
      .apply {
        parent.createDirectories()
        writeText("not json")
      }

    assertThat(assertThrows<IllegalStateException>(::migrate))
      .hasMessageThat()
      .contains("Malformed latest release")
    assertThat(
      databaseDirectory
        .resolve("releases/8.struct")
        .exists()
    ).isFalse()
  }

  @ParameterizedTest(name = "duplicate table names {0} and {1}")
  @CsvSource(value = ["books, books", "Books, books"])
  fun `fails when current structures claim duplicate table names`(
    firstTableName: String,
    secondTableName: String
  ) {
    writeStructure(
      file = databaseDirectory.resolve("a.struct"),
      structure = databaseStructure(firstTableName)
    )
    writeStructure(
      file = databaseDirectory.resolve("b.struct"),
      structure = databaseStructure(secondTableName)
    )

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains("Duplicate table 'books'")
    assertThat(exception)
      .hasMessageThat()
      .contains("a.struct")
    assertThat(exception)
      .hasMessageThat()
      .contains("b.struct")
  }

  @Test
  fun `fails when current structures claim the same index`() {
    val index = IndexStructure(
      name = "books_index",
      indexSql = "CREATE INDEX books_index ON books (id)",
      forTable = "books"
    )
    writeStructure(
      file = databaseDirectory.resolve("a.struct"),
      structure = DatabaseStructure(indices = linkedMapOf(index.name to index))
    )
    writeStructure(
      file = databaseDirectory.resolve("b.struct"),
      structure = DatabaseStructure(indices = linkedMapOf(index.name to index))
    )

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains("Duplicate index 'books_index'")
    assertThat(exception)
      .hasMessageThat()
      .contains("a.struct")
    assertThat(exception)
      .hasMessageThat()
      .contains("b.struct")
  }

  @Test
  fun `fails when current structures claim the same SQLite schema identifier across table and index`() {
    val index = IndexStructure(
      name = "books",
      indexSql = "CREATE INDEX books ON Books (id)",
      forTable = "Books"
    )
    writeStructure(
      file = databaseDirectory.resolve("a.struct"),
      structure = databaseStructure("Books")
    )
    writeStructure(
      file = databaseDirectory.resolve("b.struct"),
      structure = DatabaseStructure(
        indices = linkedMapOf(index.name to index)
      )
    )

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains("Duplicate SQLite schema identifier")
    assertThat(exception)
      .hasMessageThat()
      .contains("table 'Books'")
    assertThat(exception)
      .hasMessageThat()
      .contains("index 'books'")
    assertThat(exception)
      .hasMessageThat()
      .contains("a.struct")
    assertThat(exception)
      .hasMessageThat()
      .contains("b.struct")
  }

  @Test
  fun `fails when no current structure exists`() {
    assertThat(assertThrows<IllegalStateException>(::migrate))
      .hasMessageThat()
      .contains("No current database structure snapshots")
  }

  @Test
  fun `fails when releases path is a regular file`() {
    writeStructure(
      file = databaseDirectory.resolve("module.struct"),
      structure = databaseStructure("books")
    )
    val releasesPath = databaseDirectory.resolve("releases")
    releasesPath.writeText("not a directory")
    assetsDirectory
      .apply {
        createDirectories()
        resolve("99.sql").writeText("ignored")
      }

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains(releasesPath.toFile().absolutePath)
    assertThat(exception)
      .hasMessageThat()
      .contains("cannot be listed as a directory")
  }

  @Test
  fun `fails when database directory is a regular file`() {
    val databasePath = databaseDirectory
    databasePath.writeText("not a directory")

    val exception = assertThrows<IllegalStateException>(::migrate)

    assertThat(exception)
      .hasMessageThat()
      .contains(databasePath.toFile().absolutePath)
    assertThat(exception)
      .hasMessageThat()
      .contains("cannot be listed as a directory")
  }

  private fun writeStructure(
    file: Path,
    structure: DatabaseStructure
  ) {
    DatabaseStructureJson.write(
      file = file.toFile(),
      structure = structure
    )
  }

  private fun readStructure(file: Path) = checkNotNull(DatabaseStructureJson.read(file.toFile()))

  private fun migrate() = ReleaseMigrationCoordinator.migrate(
    projectDir = temporaryDirectory.toFile(),
    databaseDirectory = databaseDirectory.toFile(),
    variantName = "release"
  )

  private fun databaseStructure(vararg tableNames: String) = DatabaseStructure(
    tables = tableNames.associateWithTo(
      destination = linkedMapOf(),
      valueSelector = ::tableStructure
    )
  )

  private fun tableStructure(name: String) = TableStructure(
    name = name,
    schema = "CREATE TABLE IF NOT EXISTS $name (id INTEGER PRIMARY KEY)",
    columns = listOf(
      ColumnStructure(
        id = true,
        name = "id",
        sqlType = "INTEGER",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
  )
}
