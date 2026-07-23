package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelConstructionShapeContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `generates constructor-backed Kotlin table model`() {
    SqliteMagicCompilation
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
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Book_Dao.kt",
        "SqliteMagic_Book_Handler.kt",
        "BookTable.kt",
        "_Book.kt"
      )
      .withGeneratedSource("SqliteMagic_Book_Handler.kt") { generatedSource ->
        generatedSource.assertDoesNotContain("not_persisted")
      }
  }

  @Test
  fun `generates constructor-backed non-data Kotlin model without a zero-argument constructor`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MissingNoArgConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class MissingNoArgConstructor(var value: String)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MissingNoArgConstructor_Dao.kt",
        "SqliteMagic_MissingNoArgConstructor_Handler.kt",
        "MissingNoArgConstructorTable.kt",
        "_MissingNoArgConstructor.kt"
      )
  }

  @Test
  fun `generates mutable Kotlin table model`() {
    SqliteMagicCompilation
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
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_MutableAuthor_Dao.kt",
        "SqliteMagic_MutableAuthor_Handler.kt",
        "MutableAuthorTable.kt",
        "_MutableAuthor.kt"
      )
      .withGeneratedSource("SqliteMagic_MutableAuthor_Handler.kt") { generatedSource ->
        generatedSource.assertContains("created_by")
        generatedSource.assertDoesNotContain("transient_label")
      }
  }

  @Test
  fun `rejects constructor-backed models with an inaccessible primary constructor`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InaccessiblePrimaryConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class InaccessiblePrimaryConstructor private constructor(val value: String)
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table models require an accessible primary constructor",
        "InaccessiblePrimaryConstructor"
      )
  }

  @Test
  fun `rejects constructor-backed models with a required non-persisted parameter`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NonPersistedConstructorParameter.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            class NonPersistedConstructorParameter(
              @Column val value: String,
              ignored: String
            )
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "NonPersistedConstructorParameter.ignored"
      )
  }

  @Test
  fun `rejects constructor-backed models with a defaulted non-persisted parameter`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DefaultedNonPersistedConstructorParameter.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            class DefaultedNonPersistedConstructorParameter(
              @Column val value: String,
              ignored: String = ""
            )
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "DefaultedNonPersistedConstructorParameter.ignored"
      )
  }

  @Test
  fun `rejects constructor-backed models with a persisted body property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "PersistedBodyProperty.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class PersistedBodyProperty(val constructorValue: String) {
              val bodyValue: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "PersistedBodyProperty.bodyValue"
      )
  }

  @Test
  fun `rejects constructor-backed models with an unselected constructor property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UnselectedConstructorProperty.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = false)
            class UnselectedConstructorProperty(
              @Column val persisted: String,
              val unselected: String
            )
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "UnselectedConstructorProperty.unselected"
      )
  }

  @Test
  fun `rejects constructor-backed models with an ignored constructor property`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IgnoredConstructorProperty.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class IgnoredConstructorProperty(
              val persisted: String,
              @IgnoreColumn val ignored: String
            )
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must correspond exactly to primary-constructor parameters",
        "IgnoredConstructorProperty.ignored"
      )
  }

  @Test
  fun `does not infer constructor backing from a secondary constructor`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "SecondaryConstructorImmutable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class SecondaryConstructorImmutable {
              val value: String

              constructor(value: String) {
                this.value = value
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "SecondaryConstructorImmutable"
      )
  }

  @Test
  fun `preserves constructor property order in schema and reconstruction`() {
    SqliteMagicCompilation
      .compile(
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
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_OrderedConstructorModel_Dao.kt",
        "SqliteMagic_OrderedConstructorModel_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_OrderedConstructorModel_Handler.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "first_value",
          "second_value",
          "third_value"
        )
      }
      .withGeneratedSource("SqliteMagic_OrderedConstructorModel_Dao.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "firstValue",
          "secondValue",
          "thirdValue"
        )
      }
  }

  @Test
  fun `generates mutable model with a secondary zero-argument constructor`() {
    SqliteMagicCompilation
      .compile(
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
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_SecondaryNoArgConstructor_Dao.kt",
        "SqliteMagic_SecondaryNoArgConstructor_Handler.kt"
      )
  }

  @Test
  fun `generates mutable model whose primary constructor parameters have defaults`() {
    SqliteMagicCompilation
      .compile(
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
        "SqliteMagic_DefaultedPrimaryConstructor_Dao.kt",
        "SqliteMagic_DefaultedPrimaryConstructor_Handler.kt"
      )
  }

  @Test
  fun `prefers constructor backing when a model also has a zero-argument path`() {
    SqliteMagicCompilation
      .compile(
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
      .assertGeneratedSources("SqliteMagic_DualConstructionStrategy_Dao.kt")
      .withGeneratedSource("SqliteMagic_DualConstructionStrategy_Dao.kt") { generatedSource ->
        generatedSource.assertContains("DualConstructionStrategy(")
        generatedSource.assertDoesNotContain(".value =")
      }
  }

  @Test
  fun `rejects mutable properties with an inaccessible setter`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "PrivateSetter.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class PrivateSetter {
              var value: String = ""
                private set
            }
          """
        )
      )
      .assertCompilationError(
        "Mutable @Table properties must be readable and writable",
        "PrivateSetter.value"
      )
  }

  @Test
  fun `rejects mutable properties with a protected setter`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ProtectedSetter.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class ProtectedSetter {
              var value: String = ""
                protected set
            }
          """
        )
      )
      .assertCompilationError(
        "Mutable @Table properties must be readable and writable",
        "ProtectedSetter.value"
      )
  }

  @Test
  fun `rejects constructor-backed properties that cannot be read`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "PrivateConstructorProperty.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class PrivateConstructorProperty(private val value: String)
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table properties must be readable",
        "PrivateConstructorProperty.value"
      )
  }

  @Test
  fun `rejects arbitrary immutable table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ArbitraryImmutable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class ArbitraryImmutable(id: Long) {
              @Id
              val persistedId: Long = id
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "ArbitraryImmutable"
      )
  }

  @Test
  fun `does not infer constructor properties from same-named callables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CallableLookalike.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class CallableLookalike(value: String) {
              fun value(): String = value
            }
          """
        )
      )
      .assertCompilationError(
        "Table must define at least one persisted column",
        "CallableLookalike"
      )
  }

  @Test
  fun `rejects mutable properties that cannot be written`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ReadOnlyMutable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class ReadOnlyMutable {
              @Id
              val id: Long = 0
            }
          """
        )
      )
      .assertCompilationError(
        "Mutable @Table properties must be readable and writable",
        "ReadOnlyMutable.id"
      )
  }

  @Test
  fun `rejects mutable models without a zero-argument constructor`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MutableWithoutNoArgConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class MutableWithoutNoArgConstructor(initialValue: String) {
              var value: String = initialValue
            }
          """
        )
      )
      .assertCompilationError(
        "Mutable @Table models require an accessible zero-argument constructor",
        "MutableWithoutNoArgConstructor"
      )
  }

  @Test
  fun `rejects mutable models with an inaccessible zero-argument constructor`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InaccessibleNoArgConstructor.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class InaccessibleNoArgConstructor private constructor() {
              var value: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "Mutable @Table models require an accessible zero-argument constructor",
        "InaccessibleNoArgConstructor"
      )
  }

  @Test
  fun `rejects inherited persisted properties on constructor-backed models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InheritedConstructorModel.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            open class Audited {
              val createdAt: String = ""
            }

            @Table
            data class InheritedConstructorModel(
              @Id
              val id: Long
            ) : Audited()
          """
        )
      )
      .assertCompilationError(
        "Constructor-backed @Table models cannot persist inherited properties",
        "InheritedConstructorModel.createdAt"
      )
  }
}
