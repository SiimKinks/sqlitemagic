package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.view.ViewCollectionStep
import com.siimkinks.sqlitemagic.view.ViewSources
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ModelLateRoundCollisionTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("earlierDurableIdentities")
  fun `rejects a later table colliding with an earlier durable view or index identity`(
    ownerKind: String,
    initialSource: SourceFile,
    processingStepsFactory: (Environment) -> List<ProcessingStep>
  ) {
    SqliteMagicCompilation
      .compile(
        initialSource,
        processingStepsFactory = processingStepsFactory
      )
      .assertCompilationError(
        "SQL table name 'late_table' conflicts with $ownerKind name 'late_table'",
        "LateTable"
      )
  }

  @Test
  fun `rejects a later table whose artifact stem collides with an earlier durable view`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "EarlierNestedView",
          body = """
            class Outer {
              @View
              data class NestedView(
                @ViewColumn("value") val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }
            }
          """,
          packageName = PACKAGE
        ),
        processingStepsFactory = { environment ->
          listOf(
            ModelCollectionStep(environment),
            ViewCollectionStep(environment),
            generateLateTableStep(
              environment = environment,
              source = """
                package $PACKAGE

                import com.siimkinks.sqlitemagic.annotation.Table

                @Table("late_table_artifact")
                data class Outer_NestedView(val value: String)
              """
            )
          )
        }
      )
      .assertCompilationError(
        "declaration paths map to the same generated name 'Outer_NestedView'",
        "$PACKAGE.Outer.NestedView",
        "$PACKAGE.Outer_NestedView"
      )
  }

  companion object {
    @JvmStatic
    fun earlierDurableIdentities() = listOf(
      Arguments.of(
        "view",
        ViewSources.view(
          name = "EarlierView",
          body = """
            @View("late_table")
            data class EarlierView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """,
          packageName = PACKAGE
        ),
        { environment: Environment ->
          listOf(
            ModelCollectionStep(environment),
            ViewCollectionStep(environment),
            IndexCollectionStep(environment),
            generateLateTableStep(environment)
          )
        }
      ),
      Arguments.of(
        "index",
        SourceFile.kotlin(
          name = "EarlierIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("owner_table")
            data class EarlierTable(
              @Index("late_table") val value: String
            )
          """
        ),
        { environment: Environment ->
          listOf(
            ModelCollectionStep(environment),
            IndexCollectionStep(environment),
            generateLateTableStep(environment)
          )
        }
      )
    )
  }
}

private fun generateLateTableStep(
  environment: Environment,
  source: String = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Table

    @Table("late_table")
    data class LateTable(val value: String)
  """
) = GeneratedSourceStep(
  environment = environment,
  fileName = "LateTable",
  contents = source
)
