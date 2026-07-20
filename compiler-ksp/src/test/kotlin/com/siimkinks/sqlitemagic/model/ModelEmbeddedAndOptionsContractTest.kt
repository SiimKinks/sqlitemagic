package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class ModelEmbeddedAndOptionsContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `flattens nested and nullable embedded properties into schema leaves`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Shipment.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            data class Geo(
              @Column("lat") val latitude: Double,
              val longitude: Double
            ) {
              @IgnoreColumn val cachedLabel: String = ""
            }

            data class Delivery(
              @Embedded(prefix = "geo_") val geo: Geo?,
              val note: String?
            )

            @Table(persistAll = false)
            data class Shipment(
              @Id val id: String,
              @Embedded(prefix = "shipping_") val shipping: Delivery?,
              @Embedded(prefix = "billing_") val billing: Geo
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Shipment_Dao.kt",
        "SqliteMagic_Shipment_Handler.kt",
        "ShipmentTable.kt"
      )
      .withGeneratedSource("SqliteMagic_Shipment_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "shipping_geo_lat REAL DEFAULT NULL",
          "shipping_geo_longitude REAL DEFAULT NULL",
          "shipping_note TEXT DEFAULT NULL",
          "billing_lat REAL DEFAULT 0.0",
          "billing_longitude REAL DEFAULT 0.0"
        )
        generatedSource.assertDoesNotContain(
          " shipping ",
          " billing ",
          "cached_label"
        )
      }
      .withGeneratedSource("ShipmentTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "SHIPPING_GEO_LAT",
          "SHIPPING_GEO_LONGITUDE",
          "SHIPPING_NOTE",
          "BILLING_LAT",
          "BILLING_LONGITUDE"
        )
      }
      .withGeneratedSource("SqliteMagic_Shipment_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "shipping?.geo?.latitude",
          "shipping?.geo?.longitude",
          "shipping?.note",
          "shipping_geo_lat",
          "shipping_geo_longitude",
          "shipping_note",
          "cursor.isNull",
          "shipping = null",
          "Selected columns did not contain",
          "Delivery(",
          "Geo("
        )
        assertThat(
          Regex("cursor\\.isNull")
            .findAll(generatedSource)
            .count()
        ).isAtLeast(5)
        assertThat(
          Regex("&&")
            .findAll(generatedSource)
            .count()
        ).isAtLeast(3)
      }
  }

  @Test
  fun `applies transformer and relationship behavior to embedded leaves`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedLeaves.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class Money(val cents: Long)

            @ObjectToDbValue
            fun moneyToLong(value: Money): Long = value.cents

            @DbValueToObject
            fun longToMoney(value: Long): Money = Money(value)

            @Table
            data class Owner(
              @Id val id: String
            )

            data class Metadata(
              @Column(handleRecursively = false) val owner: Owner,
              val budget: Money
            )

            @Table
            data class Project(
              @Id val id: String,
              @Embedded(prefix = "meta_") val metadata: Metadata
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_Project_Handler.kt")
      .withGeneratedSource("SqliteMagic_Project_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "meta_owner TEXT",
          "meta_budget INTEGER"
        )
      }
      .withGeneratedSource("SqliteMagic_Project_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "metadata.owner.id",
          "moneyToLong",
          "longToMoney"
        )
      }
  }

  @Test
  fun `constructs and assigns mutable embedded values`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MutableEmbeddedValue.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            class MutableDetails {
              var label: String = ""
              var count: Long = 0
            }

            @Table
            data class MutableEmbeddedValue(
              @Id val id: String,
              @Embedded val details: MutableDetails
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MutableEmbeddedValue_Dao.kt",
        "SqliteMagic_MutableEmbeddedValue_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_MutableEmbeddedValue_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "MutableDetails()",
          ".label =",
          ".count ="
        )
      }
  }

  @Test
  fun `rejects embedded values without persisted leaves`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmptyEmbeddedValue.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            class EmptyDetails {
              @IgnoreColumn
              var ignored: String = ""
            }

            @Table
            data class EmptyEmbeddedValue(
              @Embedded val details: EmptyDetails
            )
          """
        )
      )
      .assertCompilationError(
        "Embedded value must define at least one persisted leaf column",
        "EmptyEmbeddedValue.details",
        "EmptyDetails"
      )
  }

  @Test
  fun `rejects scalar embedded values`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ScalarEmbeddedValue.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ScalarEmbeddedValue(
              @Embedded val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "@Embedded requires a non-scalar value type",
        "ScalarEmbeddedValue.value",
        "kotlin.String"
      )
  }

  @Test
  fun `rejects unsupported embedded model shapes`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UnsupportedEmbeddedShape.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table

            interface UnsupportedDetails {
              val value: String
            }

            @Table
            data class UnsupportedEmbeddedShape(
              @Embedded val details: UnsupportedDetails
            )
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Embedded model shape",
        "UnsupportedEmbeddedShape.details",
        "UnsupportedDetails"
      )
  }

  @Test
  fun `uses only effectively non-null unique embedded leaves as no-ID operation keys`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedUniqueKeys.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            data class ExternalKey(
              @Unique val value: String
            )

            @Table
            data class RequiredEmbeddedKey(
              @Embedded(prefix = "external_") val key: ExternalKey,
              val payload: String
            )

            @Table
            data class NullableEmbeddedKey(
              @Embedded(prefix = "external_") val key: ExternalKey?,
              val payload: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RequiredEmbeddedKey_Handler.kt",
        "SqliteMagic_NullableEmbeddedKey_Handler.kt",
        "_RequiredEmbeddedKey.kt",
        "_NullableEmbeddedKey.kt"
      )
      .withGeneratedSource("SqliteMagic_RequiredEmbeddedKey_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "external_value TEXT UNIQUE",
          "UpdateBuilder",
          "fun execute(byColumn:"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableEmbeddedKey_Handler.kt") { generatedSource ->
        generatedSource.assertContains("external_value TEXT UNIQUE")
        generatedSource.assertDoesNotContain(
          "UpdateBuilder",
          "PersistBuilder",
          "DeleteBuilder"
        )
      }
      .withGeneratedSource("_RequiredEmbeddedKey.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun RequiredEmbeddedKey.update()",
          "fun RequiredEmbeddedKey.persist()",
          "fun RequiredEmbeddedKey.delete()"
        )
      }
      .withGeneratedSource("_NullableEmbeddedKey.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "fun NullableEmbeddedKey.update()",
          "fun NullableEmbeddedKey.persist()",
          "fun NullableEmbeddedKey.delete()"
        )
      }
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
    strings = [
      "Column",
      "Id",
      "Unique",
      "Index",
      "IgnoreColumn"
    ]
  )
  fun `rejects combining embedded with physical-column annotations`(annotationName: String) {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedWith$annotationName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.*

            data class ConflictingValue(val value: String)

            @Table
            data class ConflictingContainer(
              @Embedded
              @$annotationName
              val embedded: ConflictingValue
            )
          """
        )
      )
      .assertCompilationError(
        "@Embedded cannot be combined with physical-column annotations",
        "ConflictingContainer.embedded"
      )
  }

  @Test
  fun `rejects IDs inside embedded values`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            data class ValueWithId(
              @Id val id: String
            )

            @Table
            data class EmbeddedId(
              @Embedded val value: ValueWithId
            )
          """
        )
      )
      .assertCompilationError(
        "@Id is not allowed inside an embedded value",
        "EmbeddedId.value.id"
      )
  }

  @Test
  fun `rejects embedded column collisions`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedColumnCollision.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table

            data class Address(val city: String)

            @Table
            data class CollidingAddress(
              @Column("home_city") val directCity: String,
              @Embedded(prefix = "home_") val home: Address
            )
          """
        )
      )
      .assertCompilationError(
        "Duplicate column name 'home_city'",
        "CollidingAddress.directCity",
        "CollidingAddress.home.city"
      )
  }

  @Test
  fun `rejects cyclic embedding`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CyclicEmbedding.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table

            data class RecursiveValue(
              @Embedded val child: RecursiveValue?
            )

            @Table
            data class CyclicEmbedding(
              @Embedded val root: RecursiveValue
            )
          """
        )
      )
      .assertCompilationError(
        "Cyclic embedded property graph",
        "CyclicEmbedding.root.child"
      )
  }

  @Test
  fun `rejects using a table model as an embedded value`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Coordinates(
              @Id val id: String,
              val latitude: Double
            )

            @Table
            data class Place(
              @Id val id: String,
              @Embedded val coordinates: Coordinates
            )
          """
        )
      )
      .assertCompilationError(
        "A @Table model cannot be used as an embedded value",
        "Place.coordinates",
        "Coordinates"
      )
  }

  @Test
  fun `generates temporary table SQL`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TemporaryTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.TEMPORARY])
            data class SessionValue(val value: String)
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_SessionValue_Handler.kt")
      .withGeneratedSource("SqliteMagic_SessionValue_Handler.kt") { generatedSource ->
        generatedSource.assertContains("CREATE TEMPORARY TABLE IF NOT EXISTS session_value")
      }
  }

  @Test
  fun `generates without-rowid table SQL`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "WithoutRowIdTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(
              value = "cached_accounts",
              options = [TableOption.TEMPORARY, TableOption.WITHOUT_ROWID]
            )
            data class CachedAccount(
              @Id val id: Long,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_CachedAccount_Handler.kt")
      .withGeneratedSource("SqliteMagic_CachedAccount_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE TEMPORARY TABLE IF NOT EXISTS cached_accounts",
          "WITHOUT ROWID"
        )
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `deduplicates repeated table options`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RepeatedTableOptions.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.TEMPORARY, TableOption.TEMPORARY, TableOption.WITHOUT_ROWID, TableOption.WITHOUT_ROWID])
            data class SessionValue(
              @Id val id: Long,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_SessionValue_Handler.kt")
      .withGeneratedSource("SqliteMagic_SessionValue_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE TEMPORARY TABLE IF NOT EXISTS session_value",
          "WITHOUT ROWID"
        )
        generatedSource.assertDoesNotContain(
          "CREATE TEMPORARY TEMPORARY TABLE",
          "WITHOUT ROWID WITHOUT ROWID"
        )
      }
  }

  @Test
  fun `accepts explicitly disabled auto-increment for without-rowid tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ExplicitlyDisabledWithoutRowId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.WITHOUT_ROWID])
            data class ExplicitlyDisabledWithoutRowId(
              @Id(autoIncrement = false) val id: Long,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_ExplicitlyDisabledWithoutRowId_Handler.kt")
      .withGeneratedSource("SqliteMagic_ExplicitlyDisabledWithoutRowId_Handler.kt") { generatedSource ->
        generatedSource.assertContains("WITHOUT ROWID")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `rejects without-rowid tables without an ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MissingWithoutRowIdId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.WITHOUT_ROWID])
            data class MissingWithoutRowIdId(val value: String)
          """
        )
      )
      .assertCompilationError(
        "WITHOUT_ROWID requires an explicit non-null @Id",
        "MissingWithoutRowIdId"
      )
  }

  @Test
  fun `rejects nullable IDs on without-rowid tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableWithoutRowId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.WITHOUT_ROWID])
            data class NullableWithoutRowId(
              @Id val id: String?
            )
          """
        )
      )
      .assertCompilationError(
        "WITHOUT_ROWID requires an explicit non-null @Id",
        "NullableWithoutRowId.id"
      )
  }

  @Test
  fun `rejects explicitly enabled auto-increment on without-rowid tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AutoIncrementWithoutRowId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.WITHOUT_ROWID])
            data class AutoIncrementWithoutRowId(
              @Id(autoIncrement = true) val id: Long
            )
          """
        )
      )
      .assertCompilationError(
        "WITHOUT_ROWID does not support explicit auto-increment",
        "AutoIncrementWithoutRowId.id"
      )
  }
}
