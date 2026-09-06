package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class MigrationsPublicationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `does not leave a migration asset when publishing the structure snapshot fails`() {
    val structureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile()
    check(structureFile.parentFile?.mkdirs() == true)
    check(structureFile.mkdir())
    val migrationFile = temporaryDirectory
      .resolve("src/debug/assets/1001.sql")
      .toFile()
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(idColumn)
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            idColumn,
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThrows<Exception> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists())
      .isFalse()
  }

  @Test
  fun `removes a stale migration asset when no migration is needed`() {
    val structure = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val structureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile()
    val migrationFile = temporaryDirectory
      .resolve("src/debug/assets/1001.sql")
      .toFile()
      .apply {
        parentFile.mkdirs()
        writeText("stale migration")
      }

    assertThat(
      MigrationsHandler(
        currentStructure = structure,
        previousStructure = structure,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    ).isFalse()
    assertThat(migrationFile.exists())
      .isFalse()
  }

  @Test
  fun `restores the structure snapshot when publishing the migration fails`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val structureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = previous
    )
    val migrationFile = temporaryDirectory
      .resolve("src/debug/assets/1001.sql")
      .toFile()
    check(migrationFile.mkdirs())

    assertThrows<Exception> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
  }
}
