package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class RecursiveAdapterContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `recursive tables own graph helpers without a separate artifact`() {
    SqliteMagicCompilation
      .compile(recursiveTeamSource())
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TeamMember_Adapter.kt",
        "TeamMemberTable.kt"
      )
      .withGeneratedSource("SqliteMagic_TeamMember_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_TeamMember_Adapter",
          "EntityRecursiveAdapter<TeamMember",
          "EntityDefaultIdentityAdapter<TeamMember>",
          "override val triggerTableNames: Array<String> = arrayOf(\"team_member\", \"team\")",
          "override fun insertRelationships(",
          "override fun updateRelationships(",
          "override fun persistRelationships(",
          "operations.insert(",
          "adapter = SqliteMagic_Team_Adapter",
          "entity.team?.let",
          "entity = it"
        )
      }
      .withGeneratedSource("TeamMemberTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "override fun addDeepQueryParts(",
          "internal fun addDeepQueryPartsInternal(",
          "val referencedTable0 = TeamTable.TEAM"
        )
      }
  }

  @Test
  fun `non-recursive tables do not receive graph helpers`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "SimpleModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class SimpleModel(@Id val id: String)
          """
        )
      )
      .isOk()
      .withGeneratedSource("SimpleModelTable.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "addDeepQueryParts",
          "addShallowQueryParts"
        )
      }
  }

  @Test
  fun `recursive extensions construct every operation builder directly`() {
    SqliteMagicCompilation
      .compile(recursiveTeamSource())
      .isOk()
      .withGeneratedSource("_TeamMember.kt") { generatedSource ->
        generatedSource.assertContains(
          "InsertBuilder(",
          "UpdateBuilder(",
          "PersistBuilder(",
          "DeleteBuilder(",
          "BulkInsertBuilder(",
          "BulkUpdateBuilder(",
          "BulkPersistBuilder(",
          "BulkDeleteBuilder(",
          "DeleteTableBuilder(",
          "adapter = SqliteMagic_TeamMember_Adapter"
        )
      }
  }

  @Test
  fun `recursive relationship hooks use typed coordinator calls without mutable child state`() {
    SqliteMagicCompilation
      .compile(recursiveTeamSource())
      .isOk()
      .withGeneratedSource("SqliteMagic_TeamMember_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "operations.insert(",
          "operations.update(",
          "operations.persist(",
          "entity.team?.let"
        )
      }
  }

  private fun recursiveTeamSource() = SourceFile.kotlin(
    name = "RecursiveRelationshipContract.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Column
      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table
      data class Team(
        @Id val id: String,
        val name: String = ""
      )

      @Table
      data class TeamMember(
        @Id val id: String,
        @Column(handleRecursively = true) val team: Team?
      )
    """
  )
}
