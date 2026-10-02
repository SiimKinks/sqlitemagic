package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.view.ViewCollectionStep
import com.siimkinks.sqlitemagic.view.ViewSources
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelLateRoundCollisionTest : ProcessingStepsTest {
  override val processingSteps = ::modelCollectionProcessingSteps

  @Test
  fun `rejects a later table colliding with an earlier durable view or index identity`() {
    val cases = listOf(
      LateTableCollisionCase(
        ownerKind = "view",
        initialSource = ViewSources.view(
          name = "EarlierView",
          body = """
            @View("late_table")
            data class EarlierView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """,
          packageName = PACKAGE
        ),
        processingStepsFactory = { environment ->
          listOf(
            ModelCollectionStep(environment),
            ViewCollectionStep(environment),
            IndexCollectionStep(environment),
            GenerateLateTableStep(environment)
          )
        },
      ),
      LateTableCollisionCase(
        ownerKind = "index",
        initialSource = SourceFile.kotlin(
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
        processingStepsFactory = { environment ->
          listOf(
            ModelCollectionStep(environment),
            IndexCollectionStep(environment),
            GenerateLateTableStep(environment)
          )
        },
      )
    )

    cases.forEach { testCase ->
      SqliteMagicCompilation
        .compile(
          testCase.initialSource,
          processingStepsFactory = testCase.processingStepsFactory
        )
        .assertCompilationError(
          "SQL table name 'late_table' conflicts with ${testCase.ownerKind} name 'late_table'",
          "LateTable"
        )
    }
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
                  val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
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
            GenerateLateTableStep(
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
}

private data class LateTableCollisionCase(
  val ownerKind: String,
  val initialSource: SourceFile,
  val processingStepsFactory: (Environment) -> List<ProcessingStep>
)

private class GenerateLateTableStep(
  private val environment: Environment,
  private val source: String = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Table

    @Table("late_table")
    data class LateTable(val value: String)
  """
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver): ProcessingStepResult {
    if (generated) return Continue
    generated = true
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = false),
        packageName = PACKAGE,
        fileName = "LateTable"
      )
      .bufferedWriter()
      .use { it.write(source.trimIndent()) }
    return Continue
  }
}
