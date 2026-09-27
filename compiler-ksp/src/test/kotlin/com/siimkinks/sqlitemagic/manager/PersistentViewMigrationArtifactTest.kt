package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class PersistentViewMigrationArtifactTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  private val structureFile get() = temporaryDirectory.resolve("db/latest.struct")
  private val sqlFile get() = temporaryDirectory.resolve("src/debug/assets/1001.sql")
  private val viewsFile get() = temporaryDirectory.resolve("src/debug/assets/1001.views")

  @Test
  fun `view diff matches normalized identity and retains exact previous ownership`() {
    val previous = DatabaseStructure(
      views = linkedMapOf(
        "First_View" to featureView("First_View"),
        "retired_view" to featureView("retired_view"),
        "moved_view" to featureView("moved_view")
      )
    )
    val current = DatabaseStructure(
      views = linkedMapOf(
        "FIRST_VIEW" to featureView("FIRST_VIEW"),
        "moved_view" to mainView("moved_view"),
        "added_view" to mainView("added_view")
      )
    )

    val diff = SchemaDiffer.diff(
      from = previous,
      to = current
    )

    assertThat(diff.hasPersistentTableOrIndexChanges).isFalse()
    assertThat(diff.hasPersistentChanges).isTrue()
    assertThat(diff.viewTransitions)
      .containsExactly(
        ViewTransition(
          previous = ViewSnapshot(
            name = "First_View",
            structure = previous.views.getValue("First_View")
          ),
          current = ViewSnapshot(
            name = "FIRST_VIEW",
            structure = current.views.getValue("FIRST_VIEW")
          ),
          changed = true
        ),
        ViewTransition(
          previous = ViewSnapshot(
            name = "moved_view",
            structure = previous.views.getValue("moved_view")
          ),
          current = ViewSnapshot(
            name = "moved_view",
            structure = current.views.getValue("moved_view")
          ),
          changed = true
        )
      )
      .inOrder()
    assertThat(diff.newViews)
      .containsExactly(
        ViewSnapshot(
          name = "added_view",
          structure = current.views.getValue("added_view")
        )
      )
    assertThat(diff.removedViews)
      .containsExactly(
        ViewSnapshot(
          name = "retired_view",
          structure = previous.views.getValue("retired_view")
        )
      )
    assertThat(MigrationPlanner.plan(diff).previousOwnedViewNames)
      .containsExactly(
        "First_View",
        "retired_view",
        "moved_view"
      )
      .inOrder()
  }

  @Test
  fun `view addition publishes a persistent change without a removal artifact`() {
    val current = views("new_view")

    assertThat(
      migrate(
        previous = DatabaseStructure(),
        current = current
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = emptyList()
      )
    )
    assertThat(DatabaseStructureJson.read(structureFile.toFile()))
      .isEqualTo(current)
    assertThat(sqlFile.exists())
      .isFalse()
    assertThat(viewsFile.exists())
      .isFalse()
  }

  @Test
  fun `renames removals and ownership changes carry every previous view in stable order`() {
    val previous = DatabaseStructure(
      views = linkedMapOf(
        "retired_view" to featureView("retired_view"),
        "renamed_view" to featureView("renamed_view"),
        "unchanged_view" to featureView("unchanged_view"),
        "moved_view" to featureView("moved_view")
      )
    )
    val current = DatabaseStructure(
      views = linkedMapOf(
        "RENAMED_VIEW" to featureView("RENAMED_VIEW"),
        "unchanged_view" to featureView("unchanged_view"),
        "moved_view" to mainView("moved_view")
      )
    )

    assertThat(
      migrate(
        previous = previous,
        current = current
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = listOf("retired_view", "renamed_view", "unchanged_view", "moved_view")
      )
    )
    assertThat(viewsFile.readText())
      .isEqualTo("retired_view\nrenamed_view\nunchanged_view\nmoved_view\n")
    assertThat(sqlFile.exists())
      .isFalse()
  }

  @Test
  fun `table change carries unchanged previous views before table scripts`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf("books" to table("books")),
      views = linkedMapOf("books_view" to ViewStructure(name = "books_view"))
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to table("books"),
        "authors" to table("authors")
      ),
      views = previous.views
    )

    assertThat(
      migrate(
        previous = previous,
        current = current
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = listOf("books_view")
      )
    )
    assertThat(viewsFile.readText())
      .isEqualTo("books_view\n")
    assertThat(sqlFile.readText())
      .contains("CREATE TABLE IF NOT EXISTS authors")
  }

  @Test
  fun `temporary only changes leave stale persistent assets removed`() {
    val previous = views("stable_view")
    val current = previous.copy(
      temporaryViews = linkedMapOf("temp_view" to ViewStructure(name = "temp_view"))
    )
    viewsFile.parent.createDirectories()
    viewsFile.writeText("stale\n")
    sqlFile.writeText("stale")

    assertThat(
      migrate(
        previous = previous,
        current = current
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = false,
        viewRemovalNames = emptyList()
      )
    )
    assertThat(viewsFile.exists())
      .isFalse()
    assertThat(sqlFile.exists())
      .isFalse()
  }

  @Test
  fun `unchanged retry keeps pending script and view removals`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf("books" to table("books")),
      views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to table("books"),
        "authors" to table("authors")
      ),
      views = linkedMapOf("middle_view" to ViewStructure(name = "middle_view"))
    )
    assertThat(
      migrate(
        previous = previous,
        current = current
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = listOf("old_view")
      )
    )
    val pendingSql = sqlFile
      .readText()
      .lines()
      .filter(String::isNotEmpty)
    val pendingViews = viewsFile
      .readText()
      .lines()
      .filter(String::isNotEmpty)

    assertThat(
      migrate(
        previous = current,
        current = current,
        pendingMigrationStatements = pendingSql,
        pendingViewRemovalNames = pendingViews + "old_view"
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = false,
        viewRemovalNames = listOf("old_view")
      )
    )
    assertThat(sqlFile.readText())
      .isEqualTo("CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)\n")
    assertThat(viewsFile.readText())
      .isEqualTo("old_view\n")
  }

  @Test
  fun `second same version rename appends both plans in order`() {
    val first = DatabaseStructure(
      tables = linkedMapOf("books" to table("books")),
      views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
    )
    val middle = DatabaseStructure(
      tables = linkedMapOf(
        "books" to table("books"),
        "authors" to table("authors")
      ),
      views = linkedMapOf("middle_view" to ViewStructure(name = "middle_view"))
    )
    val final = DatabaseStructure(
      tables = linkedMapOf(
        "books" to table("books"),
        "authors" to table("authors"),
        "publishers" to table("publishers")
      ),
      views = linkedMapOf("new_view" to ViewStructure(name = "new_view"))
    )
    assertThat(
      migrate(
        previous = first,
        current = middle
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = listOf("old_view")
      )
    )
    val pendingSql = sqlFile
      .readText()
      .lines()
      .filter(String::isNotEmpty)
    val pendingViews = viewsFile
      .readText()
      .lines()
      .filter(String::isNotEmpty)

    assertThat(
      migrate(
        previous = middle,
        current = final,
        pendingMigrationStatements = pendingSql,
        pendingViewRemovalNames = pendingViews
      )
    ).isEqualTo(
      MigrationResult(
        migrationHappened = true,
        viewRemovalNames = listOf("old_view", "middle_view")
      )
    )
    assertThat(sqlFile.readText())
      .isEqualTo(
        "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)\n" +
            "CREATE TABLE IF NOT EXISTS publishers (id INTEGER PRIMARY KEY)\n"
      )
    assertThat(viewsFile.readText())
      .isEqualTo("old_view\nmiddle_view\n")
  }

  @Test
  fun `view artifact failure restores snapshot and existing script`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf("books" to table("books")),
      views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to table("books"),
        "authors" to table("authors")
      ),
      views = linkedMapOf("new_view" to ViewStructure(name = "new_view"))
    )
    structureFile.parent.createDirectories()
    DatabaseStructureJson.write(
      file = structureFile.toFile(),
      structure = previous
    )
    sqlFile.parent.createDirectories()
    sqlFile.writeText("existing script")
    viewsFile.createDirectories()

    assertThrows<Exception> {
      migrate(
        previous = previous,
        current = current
      )
    }
    assertThat(DatabaseStructureJson.read(structureFile.toFile()))
      .isEqualTo(previous)
    assertThat(sqlFile.readText())
      .isEqualTo("existing script")
    assertThat(viewsFile.toFile().isDirectory)
      .isTrue()
  }

  private fun migrate(
    previous: DatabaseStructure,
    current: DatabaseStructure,
    pendingMigrationStatements: List<String> = emptyList(),
    pendingViewRemovalNames: List<String> = emptyList()
  ) = MigrationsHandler(
    currentStructure = current,
    previousStructure = previous,
    outputStructureFile = structureFile.toFile(),
    migrationOutputFile = sqlFile.toFile(),
    pendingMigrationStatements = pendingMigrationStatements,
    pendingViewRemovalNames = pendingViewRemovalNames
  ).migrate()

  private fun views(name: String) = DatabaseStructure(
    views = linkedMapOf(name to ViewStructure(name = name))
  )

  private fun featureView(name: String) = ViewStructure(
    name = name,
    moduleName = "feature"
  )

  private fun mainView(name: String) = ViewStructure(
    name = name,
    moduleName = "main"
  )

  private fun table(name: String) = migrationTable(
    name = name,
    columns = arrayListOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
  )
}
