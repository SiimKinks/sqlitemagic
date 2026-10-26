package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

internal class PersistentViewStructurePublicationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  private val structureFile get() = temporaryDirectory.resolve("db/latest.struct")
  private val sqlFile get() = temporaryDirectory.resolve("src/debug/assets/1001.sql")

  @Test
  fun `view diff matches normalized identity and retains exact previous ownership`() {
    val previous = DatabaseStructure(
      views = mapOf(
        "First_View" to featureView("First_View"),
        "retired_view" to featureView("retired_view"),
        "moved_view" to featureView("moved_view")
      )
    )
    val current = DatabaseStructure(
      views = mapOf(
        "FIRST_VIEW" to featureView("FIRST_VIEW"),
        "moved_view" to ViewStructure(name = "moved_view"),
        "added_view" to ViewStructure(name = "added_view")
      )
    )

    val diff = SchemaDiffer.diff(
      from = previous,
      to = current
    )

    assertThat(diff.hasPersistentTableOrIndexChanges).isFalse()
    assertThat(diff.hasPersistentChanges).isTrue()
    assertThat(diff).isEqualTo(
      SchemaDiff(
        previousTableNames = emptyList(),
        currentTables = emptyList(),
        transitions = emptyList(),
        newTables = emptyList(),
        removedTables = emptyList(),
        viewTransitions = listOf(
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
        ),
        newViews = listOf(
          ViewSnapshot(
            name = "added_view",
            structure = current.views.getValue("added_view")
          )
        ),
        removedViews = listOf(
          ViewSnapshot(
            name = "retired_view",
            structure = previous.views.getValue("retired_view")
          )
        )
      )
    )
  }

  @Test
  fun `view changes update structure without publishing removal resources`() {
    val previous = DatabaseStructure(views = mapOf("old_view" to ViewStructure(name = "old_view")))
    val current = DatabaseStructure(views = mapOf("new_view" to ViewStructure(name = "new_view")))
    val migrationHappened = MigrationsHandler(
      currentStructure = current,
      previousStructure = previous,
      outputStructureFile = structureFile.toFile(),
      migrationOutputFile = sqlFile.toFile()
    ).migrate()

    assertThat(migrationHappened).isTrue()
    assertThat(DatabaseStructureJson.read(structureFile.toFile())).isEqualTo(current)
    assertThat(sqlFile.exists()).isFalse()
  }

  private fun featureView(name: String) = ViewStructure(
    name = name,
    moduleName = "Feature"
  )
}
