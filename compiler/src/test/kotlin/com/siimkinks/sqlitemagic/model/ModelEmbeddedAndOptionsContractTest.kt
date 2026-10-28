package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class ModelEmbeddedAndOptionsContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `keeps nullable embedded access isolated between tables with matching paths`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_OptionalDetailsRecord_Dao.kt") {
        it.assertContains("entity.details?.`when`")
      }
      .withGeneratedSource("SqliteMagic_RequiredDetailsRecord_Dao.kt") { generatedSource ->
        generatedSource.assertContains("entity.details.`when`")
        generatedSource.assertDoesNotContain("entity.details?.")
      }
  }

  @Test
  fun `flattens nested and nullable embedded properties into schema leaves`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Shipment_Dao.kt",
        "SqliteMagic_Shipment_Adapter.kt",
        "ShipmentTable.kt"
      )
      .withGeneratedSource("SqliteMagic_Shipment_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "shipping_geo_lat REAL DEFAULT NULL",
          "shipping_geo_longitude REAL DEFAULT NULL",
          "shipping_note TEXT DEFAULT NULL",
          "billing_lat REAL NOT NULL DEFAULT 0.0",
          "billing_longitude REAL NOT NULL DEFAULT 0.0"
        )
        generatedSource.assertDoesNotContain(
          " shipping ",
          " billing ",
          "cached_label"
        )
      }
      .withGeneratedSource("ShipmentTable.kt") { generatedSource ->
        generatedSource
          .assertContainsUnwrapped(
            "val SHIPPING_GEO_LAT: Column<Shipment, Double, SqlNullable, Double?, Double?, SqlNullable, Double?>",
            "val SHIPPING_GEO_LONGITUDE: Column<Shipment, Double, SqlNullable, Double?, Double?, SqlNullable, Double?>",
            "val SHIPPING_NOTE: Column<Shipment, String, SqlNullable, String?, String?, SqlNullable, String?>",
            "val BILLING_LAT: Column<Shipment, Double, Required, Double, Double, Required, Double>",
            "val BILLING_LONGITUDE: Column<Shipment, Double, Required, Double, Double, Required, Double>"
          )
          .assertContains(": Table<Shipment>(")
        generatedSource
          .substringAfter(": Table<Shipment>(")
          .substringBefore(") {")
          .assertDoesNotContain("temporary = true")
      }
      .withGeneratedSource("SqliteMagic_Shipment_Dao.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          "shipping?.geo?.latitude",
          "shipping?.geo?.longitude",
          "shipping?.note",
          """values.put("shipping_geo_lat", it)""",
          """values.put("shipping_geo_longitude", it)""",
          """values.put("shipping_note", it)""",
          "shipping = if (column1IsNull && column2IsNull && column3IsNull) null else Delivery(",
          "geo = if (column1IsNull && column2IsNull) null else Geo(",
          """latitude = if (column1IsNull) throw SQLException("Column \"shipping_geo_lat\" was NULL")""",
          "note = if (column3IsNull) null else cursor.getString(startIndex + 3)",
          "billing = Geo(latitude = cursor.getDouble(startIndex + 4), longitude = cursor.getDouble(startIndex + 5))"
        )
        assertThat(
          Regex("""cursor\.isNull""")
            .findAll(generatedSource.functionBody("objectFromCursorPosition"))
            .count()
        ).isEqualTo(3)
      }
  }

  @Test
  fun `uses safe calls for every descendant of nullable embedded properties`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableNestedEmbedded_Dao.kt")
      .withGeneratedSource("SqliteMagic_NullableNestedEmbedded_Dao.kt") {
        it.assertContains("entity.optionalDetails?.coordinates?.latitude")
      }
  }

  @Test
  fun `applies transformer and relationship behavior to embedded leaves`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_Project_Adapter.kt")
      .withGeneratedSource("SqliteMagic_Project_Adapter.kt") {
        it.assertContains(
          "meta_owner TEXT",
          "meta_budget INTEGER"
        )
      }
      .withGeneratedSource("SqliteMagic_Project_Dao.kt") {
        it.assertContains(
          "metadata.owner.id",
          "moneyToLong",
          "longToMoney"
        )
      }
  }

  @Test
  fun `skips recursive operations below a null embedded value`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableEmbeddedProject_Adapter.kt",
        "NullableEmbeddedProjectTable.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableEmbeddedProject_Adapter.kt") {
        it.assertContains(
          "entity.metadata?.owner?.let",
          "operations.insert(",
          "adapter = SqliteMagic_EmbeddedOwner_Adapter",
          "entity = it"
        )
      }
  }

  @Test
  fun `constructs and assigns mutable embedded values`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MutableEmbeddedValue_Dao.kt",
        "SqliteMagic_MutableEmbeddedValue_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_MutableEmbeddedValue_Dao.kt") {
        it.assertContains(
          "MutableDetails()",
          ".label =",
          ".count ="
        )
      }
  }

  @Test
  fun `rejects embedded values without persisted leaves`() {
    compiler
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
    compiler
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
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RequiredEmbeddedKey_Adapter.kt",
        "SqliteMagic_NullableEmbeddedKey_Adapter.kt",
        "_RequiredEmbeddedKey.kt",
        "_NullableEmbeddedKey.kt"
      )
      .withGeneratedSource("SqliteMagic_RequiredEmbeddedKey_Adapter.kt") {
        it.assertContains(
          "external_value TEXT NOT NULL UNIQUE",
          "EntityIdentityAdapter<RequiredEmbeddedKey>",
          "override fun updateStatementSql"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableEmbeddedKey_Adapter.kt") {
        it.assertContains("external_value TEXT UNIQUE")
      }
      .withGeneratedSource("_RequiredEmbeddedKey.kt") {
        it.assertContains(
          "fun RequiredEmbeddedKey.update()",
          "fun RequiredEmbeddedKey.persist()",
          "fun RequiredEmbeddedKey.delete()"
        )
      }
      .withGeneratedSource("_NullableEmbeddedKey.kt") {
        it.assertDoesNotContain(
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
    compiler
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedWith$annotationName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.*
            import com.siimkinks.sqlitemagic.annotation.Embedded

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
    compiler
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
    compiler
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
    compiler
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
    compiler
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
  fun `generates temporary and without-rowid table SQL`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_SessionValue_Adapter.kt") {
        it.assertContains("CREATE TEMPORARY TABLE IF NOT EXISTS session_value")
      }
      .withGeneratedSource("SessionValueTable.kt") { generatedSource ->
        generatedSource.assertContains(": Table<SessionValue>(")
        generatedSource
          .substringAfter(": Table<SessionValue>(")
          .substringBefore(") {")
          .assertContains("temporary = true")
      }
      .withGeneratedSource("SqliteMagic_CachedAccount_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE TEMPORARY TABLE IF NOT EXISTS cached_accounts",
          "WITHOUT ROWID"
        )
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_ExplicitlyDisabledWithoutRowId_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("WITHOUT ROWID")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `rejects without-rowid tables without an ID`() {
    compiler
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
    compiler
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
    compiler
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

  companion object {
    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "MatchingEmbeddedPaths.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              data class SharedDetails(val `when`: String)

              @Table
              data class OptionalDetailsRecord(
                @Id val id: Long,
                @Embedded val details: SharedDetails?
              )

              @Table
              data class RequiredDetailsRecord(
                @Id val id: Long,
                @Embedded val details: SharedDetails
              )
            """
          ),
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
          ),
          SourceFile.kotlin(
            name = "NullableNestedEmbedded.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              data class Coordinates(
                val latitude: Double
              )

              data class OptionalDetails(
                @Embedded val coordinates: Coordinates
              )

              @Table
              data class NullableNestedEmbedded(
                @Id val id: String,
                @Embedded val optionalDetails: OptionalDetails?
              )
            """
          ),
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
          ),
          SourceFile.kotlin(
            name = "NullableEmbeddedRelationship.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              data class EmbeddedOwner(
                @Id val id: String,
                val name: String
              )

              data class OptionalMetadata(
                @Column(handleRecursively = true)
                val owner: EmbeddedOwner
              )

              @Table
              data class NullableEmbeddedProject(
                @Id val id: String,
                @Embedded val metadata: OptionalMetadata?
              )
            """
          ),
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
          ),
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
          ),
          SourceFile.kotlin(
            name = "TemporaryTable.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.TableOption

              @Table(options = [TableOption.TEMPORARY])
              data class SessionValue(val value: String)
            """
          ),
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
          ),
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
          ),
          processingStepsFactory = ::modelProcessingSteps
        )
        .generatedSources()
    }
  }
}
