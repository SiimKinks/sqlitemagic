package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ModelColumnValidationContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "empty table class, @Table, class EmptyTable",
    "unpersisted constructor property, @Table(persistAll = false), data class EmptyTable(val ignored: String)"
  )
  fun `rejects tables without persisted columns`(
    label: String,
    annotation: String,
    declaration: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "EmptyTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            $annotation
            $declaration
          """
        )
      )
      .assertCompilationError(
        "Table must define at least one persisted column",
        "EmptyTable"
      )
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "same column name, duplicate, duplicate, Duplicate column name 'duplicate'",
    "same generated field name, fooBar, foo_bar, Duplicate generated column name 'FOO_BAR'"
  )
  fun `rejects colliding column names`(
    label: String,
    firstName: String,
    secondName: String,
    diagnostic: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "DuplicateColumns.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DuplicateColumns(
              @Column("$firstName") val first: String,
              @Column("$secondName") val second: String
            )
          """
        )
      )
      .assertCompilationError(
        diagnostic,
        "DuplicateColumns.first",
        "DuplicateColumns.second"
      )
  }

  @Test
  fun `rejects multiple ids`() {
    compiler
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

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "BLOB explicit ID, BlobId, ByteArray",
    "transformed BLOB explicit ID, TransformedBlobId, BinaryId"
  )
  fun `rejects BLOB explicit IDs`(
    label: String,
    tableName: String,
    idType: String
  ) {
    val transformer = when (idType) {
      "BinaryId" -> """
        data class BinaryId(val value: String)

        @ObjectToDbValue
        fun binaryIdToBytes(value: BinaryId): ByteArray = value.value.encodeToByteArray()

        @DbValueToObject
        fun bytesToBinaryId(value: ByteArray): BinaryId = BinaryId(value.decodeToString())
      """
      else -> ""
    }
    compiler
      .compile(
        SourceFile.kotlin(
          name = "$tableName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            $transformer

            @Table
            data class $tableName(
              @Id val id: $idType
            )
          """
        )
      )
      .assertCompilationError(
        "BLOB storage types cannot be used as explicit @Id columns",
        "$tableName.id"
      )
  }

  @Test
  fun `rejects unsupported persisted property types without a transformer`() {
    compiler
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
