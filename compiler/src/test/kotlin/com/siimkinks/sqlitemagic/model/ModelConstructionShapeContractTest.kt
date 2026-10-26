package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ModelConstructionShapeContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generates constructor-backed Kotlin table models in constructor order`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "ConstructorBackedModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(
              value = "books",
              persistAll = false
            )
            data class Book(
              @Id
              val isbn: String,
              @Column("display_title")
              val title: String
            ) {
              val notPersisted: String = ""
            }
          """
        ),
        SourceFile.kotlin(
          name = "MissingNoArgConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class MissingNoArgConstructor(var value: String)
          """
        ),
        SourceFile.kotlin(
          name = "OrderedConstructorModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class OrderedConstructorModel(
              val firstValue: String,
              val secondValue: Long,
              val thirdValue: Double
            )
          """
        ),
        SourceFile.kotlin(
          name = "DualConstructionStrategy.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class DualConstructionStrategy(var value: String = "")
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Book_Dao.kt",
        "SqliteMagic_Book_Adapter.kt",
        "BookTable.kt",
        "_Book.kt"
      )
      .assertGeneratedSources(
        "SqliteMagic_MissingNoArgConstructor_Dao.kt",
        "SqliteMagic_MissingNoArgConstructor_Adapter.kt",
        "MissingNoArgConstructorTable.kt",
        "_MissingNoArgConstructor.kt"
      )
      .withGeneratedSource("SqliteMagic_Book_Adapter.kt") {
        it.assertDoesNotContain("not_persisted")
      }
      .withGeneratedSource("SqliteMagic_OrderedConstructorModel_Adapter.kt") {
        it.assertContainsInOrder(
          "first_value",
          "second_value",
          "third_value"
        )
      }
      .withGeneratedSource("SqliteMagic_OrderedConstructorModel_Dao.kt") {
        it.assertContainsInOrder(
          "firstValue",
          "secondValue",
          "thirdValue"
        )
      }
      .withGeneratedSource("SqliteMagic_DualConstructionStrategy_Dao.kt") { generatedSource ->
        generatedSource.assertContains("DualConstructionStrategy(")
        generatedSource.assertDoesNotContain(".value =")
      }
  }

  @Test
  fun `generates mutable Kotlin table models`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "MutableModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            open class MutableBase {
              var createdBy: String = ""
            }

            @Table
            class MutableAuthor : MutableBase() {
              @Id
              var id: Long = 0
              var displayName: String = ""
              @IgnoreColumn
              var transientLabel: String = ""
            }
          """
        ),
        SourceFile.kotlin(
          name = "SecondaryNoArgConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class SecondaryNoArgConstructor(initialValue: String) {
              var value: String = initialValue

              constructor() : this("")
            }
          """
        ),
        SourceFile.kotlin(
          name = "DefaultedPrimaryConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class DefaultedPrimaryConstructor(initialValue: String = "") {
              var value: String = initialValue
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MutableAuthor_Dao.kt",
        "SqliteMagic_MutableAuthor_Adapter.kt",
        "MutableAuthorTable.kt",
        "_MutableAuthor.kt"
      )
      .assertGeneratedSources(
        "SqliteMagic_SecondaryNoArgConstructor_Dao.kt",
        "SqliteMagic_SecondaryNoArgConstructor_Adapter.kt"
      )
      .assertGeneratedSources(
        "SqliteMagic_DefaultedPrimaryConstructor_Dao.kt",
        "SqliteMagic_DefaultedPrimaryConstructor_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_MutableAuthor_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("created_by")
        generatedSource.assertDoesNotContain("transient_label")
      }
      .withGeneratedSource("SqliteMagic_MutableAuthor_Dao.kt") {
        it.assertContainsUnwrapped(
          "MutableAuthor().apply { this.id = id }",
          "MutableAuthor().apply { this.createdBy = cursor.getString(startIndex) " +
              "this.id = cursor.getLong(startIndex + 1) this.displayName = cursor.getString(startIndex + 2) }"
        )
      }
  }

  @Test
  fun `preserves nullable mutable property, embedded and relationship defaults when values are null`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "MutableNullableDefaults.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class MutableNullableDefaults {
              @Id
              var id: Long = 0
              var label: String? = "default-label"
              var count: Int? = 42
            }
          """
        ),
        SourceFile.kotlin(
          name = "MutableNullableCompositeDefaults.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            class MutableNullableDetails {
              var label: String = "default-label"
              var count: Long = 42
            }

            @Table
            class MutableNullableOwner {
              @Id
              var id: Long = 1
            }

            @Table
            class MutableNullableCompositeDefaults {
              @Id
              var id: Long = 0
              @Embedded
              var details: MutableNullableDetails? = MutableNullableDetails()
              var owner: MutableNullableOwner? = MutableNullableOwner()
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MutableNullableDefaults_Dao.kt",
        "SqliteMagic_MutableNullableCompositeDefaults_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_MutableNullableDefaults_Dao.kt") {
        it.assertContains(
          "if (!cursor.isNull(startIndex + 1)) this.label = cursor.getString(startIndex + 1)",
          "if (!cursor.isNull(startIndex + 2)) this.count = cursor.getInt(startIndex + 2)"
        )
      }
      .withGeneratedSource("SqliteMagic_MutableNullableCompositeDefaults_Dao.kt") {
        it.assertContains(
          "if (!column1IsNull || !column2IsNull) this.details =",
          "this.label =",
          "this.count =",
          "if (!cursor.isNull(startIndex + 3)) this.owner = (related.read(cursor, 0) as MutableNullableOwner?) ?: " +
              "SqliteMagic_MutableNullableOwner_Dao.newInstanceWithOnlyId(cursor.getLong(startIndex + 3))"
        )
      }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidModelShapes")
  fun `rejects models outside the construction contract`(
    label: String,
    modelName: String,
    body: String,
    diagnostic: String,
    subject: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "$modelName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            $body
          """
        )
      )
      .assertCompilationError(diagnostic, subject)
  }

  companion object {
    @JvmStatic
    fun invalidModelShapes() = listOf(
      Arguments.of(
        "rejects constructor-backed models with an inaccessible primary constructor",
        "InaccessiblePrimaryConstructor",
        """
          @Table
          class InaccessiblePrimaryConstructor private constructor(val value: String)
        """,
        "Constructor-backed @Table models require an accessible primary constructor",
        "InaccessiblePrimaryConstructor"
      ),
      Arguments.of(
        "rejects constructor-backed models with a required non-persisted parameter",
        "NonPersistedConstructorParameter",
        """
          @Table(persistAll = false)
          class NonPersistedConstructorParameter(
            @Column val value: String,
            ignored: String
          )
        """,
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "NonPersistedConstructorParameter.ignored"
      ),
      Arguments.of(
        "rejects constructor-backed models with a defaulted non-persisted parameter",
        "DefaultedNonPersistedConstructorParameter",
        """
          @Table(persistAll = false)
          class DefaultedNonPersistedConstructorParameter(
            @Column val value: String,
            ignored: String = ""
          )
        """,
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "DefaultedNonPersistedConstructorParameter.ignored"
      ),
      Arguments.of(
        "rejects constructor-backed models with a persisted body property",
        "PersistedBodyProperty",
        """
          @Table
          class PersistedBodyProperty(val constructorValue: String) {
            val bodyValue: String = ""
          }
        """,
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "PersistedBodyProperty.bodyValue"
      ),
      Arguments.of(
        "rejects constructor-backed models with an unselected constructor property",
        "UnselectedConstructorProperty",
        """
          @Table(persistAll = false)
          class UnselectedConstructorProperty(
            @Column val persisted: String,
            val unselected: String
          )
        """,
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "UnselectedConstructorProperty.unselected"
      ),
      Arguments.of(
        "rejects constructor-backed models with an ignored constructor property",
        "IgnoredConstructorProperty",
        """
          @Table
          class IgnoredConstructorProperty(
            val persisted: String,
            @IgnoreColumn val ignored: String
          )
        """,
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "IgnoredConstructorProperty.ignored"
      ),
      Arguments.of(
        "does not infer constructor backing from a secondary constructor",
        "SecondaryConstructorImmutable",
        """
          @Table
          class SecondaryConstructorImmutable {
            val value: String

            constructor(value: String) {
              this.value = value
            }
          }
        """,
        "Unsupported @Table model shape",
        "SecondaryConstructorImmutable"
      ),
      Arguments.of(
        "rejects mutable properties with an inaccessible setter",
        "PrivateSetter",
        """
          @Table
          class PrivateSetter {
            var value: String = ""
              private set
          }
        """,
        "Mutable @Table properties must be readable and writable",
        "PrivateSetter.value"
      ),
      Arguments.of(
        "rejects mutable properties with a protected setter",
        "ProtectedSetter",
        """
          @Table
          class ProtectedSetter {
            var value: String = ""
              protected set
          }
        """,
        "Mutable @Table properties must be readable and writable",
        "ProtectedSetter.value"
      ),
      Arguments.of(
        "rejects constructor-backed properties that cannot be read",
        "PrivateConstructorProperty",
        """
          @Table
          class PrivateConstructorProperty(private val value: String)
        """,
        "Constructor-backed @Table properties must be readable",
        "PrivateConstructorProperty.value"
      ),
      Arguments.of(
        "rejects arbitrary immutable table models",
        "ArbitraryImmutable",
        """
          @Table
          class ArbitraryImmutable(id: Long) {
            @Id
            val persistedId: Long = id
          }
        """,
        "Unsupported @Table model shape",
        "ArbitraryImmutable"
      ),
      Arguments.of(
        "does not infer constructor properties from same-named callables",
        "CallableLookalike",
        """
          @Table
          class CallableLookalike(value: String) {
            fun value(): String = value
          }
        """,
        "Table must define at least one persisted column",
        "CallableLookalike"
      ),
      Arguments.of(
        "rejects mutable properties that cannot be written",
        "ReadOnlyMutable",
        """
          @Table
          class ReadOnlyMutable {
            @Id
            val id: Long = 0
          }
        """,
        "Mutable @Table properties must be readable and writable",
        "ReadOnlyMutable.id"
      ),
      Arguments.of(
        "rejects mutable models without a zero-argument constructor",
        "MutableWithoutNoArgConstructor",
        """
          @Table
          class MutableWithoutNoArgConstructor(initialValue: String) {
            var value: String = initialValue
          }
        """,
        "Mutable @Table models require an accessible zero-argument constructor",
        "MutableWithoutNoArgConstructor"
      ),
      Arguments.of(
        "rejects mutable models with an inaccessible zero-argument constructor",
        "InaccessibleNoArgConstructor",
        """
          @Table
          class InaccessibleNoArgConstructor private constructor() {
            var value: String = ""
          }
        """,
        "Mutable @Table models require an accessible zero-argument constructor",
        "InaccessibleNoArgConstructor"
      ),
      Arguments.of(
        "rejects inherited persisted properties on constructor-backed models",
        "InheritedConstructorModel",
        """
          open class Audited {
            val createdAt: String = ""
          }

          @Table
          data class InheritedConstructorModel(
            @Id
            val id: Long
          ) : Audited()
        """,
        "Constructor-backed @Table models cannot persist inherited properties",
        "InheritedConstructorModel.createdAt"
      )
    )
  }
}
