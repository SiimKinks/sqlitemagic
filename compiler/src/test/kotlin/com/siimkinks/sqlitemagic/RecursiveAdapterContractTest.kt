package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class RecursiveAdapterContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `recursive tables own graph helpers without a separate artifact or unused result bindings`() {
    compiler
      .compile(
        SourceFile.kotlin(
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
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TeamMember_Adapter.kt",
        "TeamMemberTable.kt"
      )
      .withGeneratedSource("SqliteMagic_TeamMember_Adapter.kt") {
        it
          .assertContainsUnwrapped(
            "internal object SqliteMagic_TeamMember_Adapter",
            "EntityRecursiveAdapter<TeamMember",
            "EntityDefaultIdentityAdapter<TeamMember>",
            """override val triggerTableNames: Array<String> = arrayOf("team_member", "team")""",
            "override fun insertRelationships(",
            "override fun updateRelationships(",
            "override fun persistRelationships(",
            "operations.update(",
            "adapter = SqliteMagic_Team_Adapter",
            "entity.team?.let",
            "entity = it"
          )
          .assertContains(
            "if (operations.insert(",
            "is EntityInsertResult.Ignored",
            "if (operations.persist(",
            "is EntityPersistResult.Ignored"
          )
          .assertDoesNotContain(
            "when (val result = operations.insert(",
            "when (val result = operations.persist("
          )
      }
  }
}
