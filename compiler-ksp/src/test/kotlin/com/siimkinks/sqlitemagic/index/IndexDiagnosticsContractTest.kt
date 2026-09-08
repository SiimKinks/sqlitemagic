package com.siimkinks.sqlitemagic.index

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class IndexDiagnosticsContractTest : ProcessingStepsTest {
  override val processingSteps = ::indexProcessingSteps

  @Test
  fun `rejects dangling composite membership`() {
    SqliteMagicCompilation
      .compile(
        invalidIndexSource(
          indexDeclaration = ""
        )
      )
      .assertCompilationError(
        "Composite membership 'declared_lookup' has no matching class-level @Index"
      )
  }

  @Test
  fun `rejects unmatched composite membership despite an unrelated deferred table`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UnmatchedMembershipWithDeferredTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("deferred_tables")
            data class DeferredTable(
              val value: GeneratedDeferredValue
            )

            @Table("unmatched_memberships")
            class UnmatchedMembershipTable {
              var persistedValue: String = ""

              @IgnoreColumn
              @Column(belongsToIndex = "unmatched_lookup")
              var unmatchedValue: String = ""
            }
          """
        ),
        processingStepsFactory = { environment ->
          listOf(GeneratedDeferredValueStep(environment)) + indexProcessingSteps(environment)
        }
      )
      .assertCompilationError(
        "belongsToIndex 'unmatched_lookup' is only valid on a persisted property of a @Table"
      )
  }

  @Test
  fun `rejects a named composite with no members`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmptyComposite.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Index("empty_lookup")
            @Table("empty_composites")
            data class EmptyComposite(
              @Column val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "Composite index 'empty_lookup' must include at least one persisted member"
      )
  }

  @Test
  fun `rejects an index on a property excluded by persistAll`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ExcludedIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(value = "excluded_indexes", persistAll = false)
            class ExcludedIndex {
              @Column var persistedValue: String = ""
              @Index var excludedValue: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "@Index property is not persisted when Table.persistAll is false: ExcludedIndex.excludedValue"
      )
  }

  @Test
  fun `rejects an index on an ignored property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IgnoredIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("ignored_indexes")
            class IgnoredIndex {
              var persistedValue: String = ""

              @IgnoreColumn
              @Index("ignored_value")
              var ignoredValue: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "@Index cannot be used on an ignored property: IgnoredIndex.ignoredValue"
      )
  }

  @Test
  fun `rejects duplicate index names`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InvalidIndexNames.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("invalid_index_names")
            data class InvalidIndexNames(
              @Index("same_name") val firstValue: String,
              @Index("SAME_NAME") val secondValue: String
            )
          """
        )
      )
      .assertCompilationError(
        "Duplicate index name 'same_name'"
      )
  }

  @Test
  fun `rejects reserved index names`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ReservedIndexName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("reserved_index_names")
            data class ReservedIndexName(
              @Index("sqlite_reserved") val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "Index name 'sqlite_reserved' is reserved"
      )
  }

  @Test
  fun `rejects an index name colliding with its table`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TableIndexNameCollision.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("named_objects")
            data class TableIndexNameCollision(
              @Index("NAMED_OBJECTS") val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "Index name 'NAMED_OBJECTS' conflicts with table name 'named_objects'"
      )
  }

  @Test
  fun `rejects unsafe index names`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UnsafeIndexName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("unsafe_index_names")
            data class UnsafeIndexName(
              @Index("unsafe\nname") val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "Index name must not contain line breaks"
      )
  }

  @Test
  fun `rejects class index annotations outside table contexts`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OrphanIndexes.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index

            @Index("orphan_class")
            class OrphanClassIndex
          """
        )
      )
      .assertCompilationError(
        "@Index is only valid on a @Table or a persisted property of a @Table",
        "orphan_class"
      )
  }

  @Test
  fun `rejects property index annotations outside table contexts`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OrphanPropertyIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index

            class OrphanPropertyIndex {
              @Index("orphan_property")
              val value: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "@Index is only valid on a @Table or a persisted property of a @Table",
        "orphan_property"
      )
  }

  @Test
  fun `rejects an index on a companion property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CompanionIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("companion_indexes")
            class CompanionIndex {
              var persistedValue: String = ""

              companion object {
                @Index("companion_value")
                var companionValue: String = ""
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@Index is only valid on instance table properties: CompanionIndex.companionValue"
      )
  }

  @Test
  fun `rejects an index on a top-level property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TopLevelIndex.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index

            @Index("top_level_value")
            val topLevelValue: String = ""
          """
        )
      )
      .assertCompilationError(
        "@Index is only valid on an instance property of a @Table: topLevelValue"
      )
  }

  @Test
  fun `rejects an index on a static table field`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.java(
          name = "StaticIndex.java",
          contents = """
            package $PACKAGE;

            import com.siimkinks.sqlitemagic.annotation.Index;
            import com.siimkinks.sqlitemagic.annotation.Table;

            @Table("static_indexes")
            public class StaticIndex {
              public String persistedValue = "";

              @Index("static_value")
              public static String staticValue = "";
            }
          """
        )
      )
      .assertCompilationError(
        "@Index is only valid on instance table properties: StaticIndex.staticValue"
      )
  }

  @Test
  fun `rejects a composite membership on a non-table declaration`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OrphanMembership.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column

            class OrphanMembership {
              @Column(belongsToIndex = "orphan_lookup")
              val value: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "belongsToIndex 'orphan_lookup' is only valid on a persisted property of a @Table"
      )
  }
}

private class GeneratedDeferredValueStep(
  private val environment: Environment
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver): ProcessingStepResult {
    if (generated) return Continue
    generated = true
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = false),
        packageName = PACKAGE,
        fileName = "GeneratedDeferredValue"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("generated_deferred_values")
            data class GeneratedDeferredValue(
              @Id(autoIncrement = false) val value: String
            )
          """
        )
      }
    return Continue
  }
}
