package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.nullableObjectTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class GenClassesManagerContractTest : ProcessingStepsTest {
  override val processingSteps = ::genClassesManagerProcessingSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `generates the main database from optional and non-Long ID tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MainDatabaseTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Database
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Database(name = "library.db", version = 7)
            class LibraryDatabase

            @Table("notes")
            data class Note(
              val text: String
            )

            @Table("books")
            data class Book(
              @Id(autoIncrement = false) val isbn: String,
              val title: String
            )
          """
        ),
        kspOptions = mapOf(
          "sqlitemagic.db.version" to "7",
          "sqlitemagic.variant.debug" to "true"
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "public class SqliteMagicDatabase : GeneratedDatabase",
          "override fun getDbName(): String? = \"library.db\"",
          "override fun getDbVersion(): Int = 7",
          "override fun isDebug(): Boolean = true",
          "SqliteMagic_Note_Adapter.TABLE_SCHEMA",
          "SqliteMagic_Book_Adapter.TABLE_SCHEMA",
          "override fun getNrOfTables(moduleName: String?): Int = 2"
        )
      }
  }

  @Test
  fun `separates persistent and temporary schemas and preserves relationship order`() {
    SqliteMagicCompilation
      .compile(schemaTables())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "override fun configureDatabase(db: SupportSQLiteDatabase)",
          "db.setForeignKeyConstraintsEnabled(true)",
          "override fun createSchema(db: SupportSQLiteDatabase)",
          "db.execSQL(SqliteMagic_Parent_Adapter.TABLE_SCHEMA)",
          "db.execSQL(SqliteMagic_Child_Adapter.TABLE_SCHEMA)",
          "override fun createTemporarySchema(db: SupportSQLiteDatabase)",
          "db.execSQL(SqliteMagic_SessionOwner_Adapter.TABLE_SCHEMA)",
          "db.execSQL(SqliteMagic_SessionCache_Adapter.TABLE_SCHEMA)",
          "override fun migrateViews(db: SupportSQLiteDatabase): Unit = Unit"
        )
        generatedSource.assertDoesNotContain(
          "createSchema(db: SupportSQLiteDatabase) {\n" +
              "    db.execSQL(SqliteMagic_SessionCache_Adapter.TABLE_SCHEMA)"
        )
        check(
          generatedSource.indexOf("SqliteMagic_Parent_Adapter.TABLE_SCHEMA") <
              generatedSource.indexOf("SqliteMagic_Child_Adapter.TABLE_SCHEMA")
        )
        check(
          generatedSource.indexOf("SqliteMagic_SessionOwner_Adapter.TABLE_SCHEMA") <
              generatedSource.indexOf("SqliteMagic_SessionCache_Adapter.TABLE_SCHEMA")
        )
      }
  }

  @Test
  fun `generates clear counts transformer dispatch and the empty-module gate`() {
    SqliteMagicCompilation
      .compile(SourceFile.kotlin(name = "Empty.kt", contents = "package $PACKAGE\nclass Empty"))
      .isOk()
      .apply {
        check("SqliteMagicDatabase.kt" !in generatedSourceNames())
      }

    SqliteMagicCompilation
      .compile(transformerTable())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "override fun clearData(db: SupportSQLiteDatabase): StringArraySet",
          "db.execSQL(\"DELETE FROM transformed_values\")",
          "allChangedTables.add(\"transformed_values\")",
          "TokenColumn<",
          "val sqlValue = TokenTransformers.toDatabaseValue(input as Token)\n" +
              "        val stringValue = sqlValue\n" +
              "        TokenColumn",
          "val sqlValue = NumericTokenTransformers.toDatabaseValue(input as NumericToken)\n" +
              "        val stringValue = sqlValue.toString()\n" +
              "        NumericTokenColumn",
          "else -> Column<V, V, V, Any, NotNullable>(",
          $$""""'${input}'""""
        )
      }
  }

  @Test
  fun `generates public submodule aggregation and module-aware adapters`() {
    val submodule = SqliteMagicCompilation
      .compile(submoduleDatabase())
      .isOk()
      .assertGeneratedSources(
        "FeatureGeneratedClassesManager.kt",
        "SqliteMagic_FeatureItem_Adapter.kt"
      )
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") { generatedSource ->
        generatedSource.assertContains(
          "public object FeatureGeneratedClassesManager",
          "public fun getNrOfTables(moduleName: String?): Int = 1",
          "public fun <V : Any> columnForValueOrNull("
        )
      }
      .withGeneratedSource("SqliteMagic_FeatureItem_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("override val moduleName: String? = \"Feature\"")
      }

    submodule
      .compile(mainDatabaseWithSubmodule())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "FeatureGeneratedClassesManager.configureDatabase(db)",
          "FeatureGeneratedClassesManager.createSchema(db)",
          "FeatureGeneratedClassesManager.createTemporarySchema(db)",
          "allChangedTables.addAll(FeatureGeneratedClassesManager.clearData(db))",
          "override fun getSubmoduleNames(): Array<String>? = arrayOf(\"Feature\")",
          "\"Feature\" -> FeatureGeneratedClassesManager.getNrOfTables(moduleName)",
          "FeatureGeneratedClassesManager.columnForValueOrNull(className = className, input = input)"
        )
      }
  }

  @Test
  fun `persists table-only debug migration state and excludes temporary tables`() {
    SqliteMagicCompilation
      .compile(
        schemaTables(),
        kspOptions = mapOf(
          "sqlitemagic.migrate.debug" to "true",
          "sqlitemagic.project.dir" to temporaryDirectory.toString(),
          "sqlitemagic.variant.name" to "debug",
          "sqlitemagic.variant.debug" to "true"
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")

    Files
      .readString(temporaryDirectory.resolve("db/latest.struct"))
      .apply {
        assertContains(
          "\"parents\"",
          "\"children\"",
          "\"indices\":{}"
        )
        assertDoesNotContain("session_cache")
        assertDoesNotContain("session_owners")
      }
  }

  @Test
  fun `includes tables generated in later rounds before finalizing the manager`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Initial.kt",
          contents = "package $PACKAGE\nclass Initial"
        ),
        processingStepsFactory = { environment ->
          genClassesManagerProcessingSteps(environment)
            .toMutableList()
            .apply {
              add(
                index = lastIndex,
                element = LateTableGenerationStep(environment)
              )
            }
        }
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LateTable_Adapter.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "db.execSQL(SqliteMagic_LateTable_Adapter.TABLE_SCHEMA)",
          "override fun getNrOfTables(moduleName: String?): Int = 1"
        )
      }
  }

  @Test
  fun `guards nullable serializers and rejects erased transformer ambiguity`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        nullableObjectTransformer(),
        erasedTransformerAmbiguity()
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "val sqlValue = NullableEmailTransformer.emailToString(input as Email)\n" +
              "        val stringValue = sqlValue\n" +
              "          ?: throw NullPointerException(\"SQL argument cannot be null\")",
          "throw UnsupportedOperationException(",
          "\"Unable to disambiguate transformer for kotlin.collections.List\""
        )
      }
  }

  private fun transformerTable() = SourceFile.kotlin(
    name = "TransformerTable.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
      import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

      data class Token(val value: String)

      data class NumericToken(val value: Long)

      object TokenTransformers {
        @ObjectToDbValue
        fun toDatabaseValue(value: Token): String = value.value

        @DbValueToObject
        fun fromDatabaseValue(value: String): Token = Token(value)
      }

      object NumericTokenTransformers {
        @ObjectToDbValue
        fun toDatabaseValue(value: NumericToken): Long = value.value

        @DbValueToObject
        fun fromDatabaseValue(value: Long): NumericToken = NumericToken(value)
      }

      @Table("transformed_values")
      data class TransformedValue(
        val token: Token,
        val numericToken: NumericToken
      )
    """
  )

  private fun mainDatabaseWithSubmodule() = SourceFile.kotlin(
    name = "MainDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(submodules = [FeatureDatabase::class])
      class MainDatabase
    """
  )

  private fun erasedTransformerAmbiguity() = SourceFile.kotlin(
    name = "ErasedTransformerAmbiguity.kt",
    contents = """
      package $PACKAGE.transformers

      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
      import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

      data class Phone(val value: String)

      object GenericTransformers {
        @ObjectToDbValue
        fun emailsToString(values: List<Email>): String = values.joinToString(transform = Email::value)

        @DbValueToObject
        fun stringToEmails(value: String): List<Email> = value.split(',').map(::Email)

        @ObjectToDbValue
        fun phonesToString(values: List<Phone>): String = values.joinToString(transform = Phone::value)

        @DbValueToObject
        fun stringToPhones(value: String): List<Phone> = value.split(',').map(::Phone)
      }

      @Table("contacts")
      data class Contact(val name: String)
    """
  )
}

private class LateTableGenerationStep(
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
        fileName = "LateTable"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("late_tables")
            data class LateTable(val value: String)
          """.trimIndent()
        )
      }
    return Continue
  }
}
