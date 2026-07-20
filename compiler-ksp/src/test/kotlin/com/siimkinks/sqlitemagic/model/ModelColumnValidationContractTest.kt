package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelColumnValidationContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `rejects empty tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmptyTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class EmptyTable
          """
        )
      )
      .assertCompilationError(
        "Table must define at least one persisted column",
        "EmptyTable"
      )
  }

  @Test
  fun `rejects tables without persisted columns`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmptyTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            data class EmptyTable(val ignored: String)
          """
        )
      )
      .assertCompilationError(
        "Table must define at least one persisted column",
        "EmptyTable"
      )
  }

  @Test
  fun `rejects duplicate column names`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DuplicateColumns.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DuplicateColumns(
              @Column("duplicate") val first: String,
              @Column("duplicate") val second: String
            )
          """
        )
      )
      .assertCompilationError(
        "Duplicate column name 'duplicate'",
        "DuplicateColumns.first",
        "DuplicateColumns.second"
      )
  }

  @Test
  fun `rejects multiple ids`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DuplicateIds.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DuplicateIds(
              @Id val firstId: String,
              @Id val secondId: String
            )
          """
        )
      )
      .assertCompilationError(
        "Table must declare at most one @Id",
        "DuplicateIds.firstId",
        "DuplicateIds.secondId"
      )
  }

  @Test
  fun `rejects unsupported persisted property types without a transformer`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UnsupportedPropertyType.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import java.net.URI

            @Table
            data class UnsupportedPropertyType(val value: URI)
          """
        )
      )
      .assertCompilationError(
        "Persisted property type must be a supported SQLite type, transformed type, or @Table relationship",
        "UnsupportedPropertyType.value",
        "java.net.URI"
      )
  }
}
