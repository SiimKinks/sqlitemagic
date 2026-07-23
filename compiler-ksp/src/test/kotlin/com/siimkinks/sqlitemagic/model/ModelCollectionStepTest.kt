package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.AutoIncrementMode.DISABLED
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionStep
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.STRING
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.IdentityHashMap

internal class ModelCollectionStepTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `collects complete durable table value`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Book.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table(
              value = "books",
              persistAll = false,
              options = [TableOption.TEMPORARY]
            )
            data class Book(
              @Id(autoIncrement = false)
              @Column("book_id")
              val id: String,
              @Column(belongsToIndex = "books_title")
              @Index(
                value = "title_lookup",
                unique = true
              )
              @Unique
              val title: String?,
              @Column(belongsToIndex = "books_title")
              val subtitle: String
            )
          """
        )
      )
      .isOk()
    val environment = compilation.environment
    val table = environment.tableElements.values.single()
    val idPath = mockPropertyPath("id")
    val titlePath = mockPropertyPath("title")
    val subtitlePath = mockPropertyPath("subtitle")
    val stringType = mockParsedType(typeName = STRING)
    val nullableStringType = mockParsedType(typeName = STRING.copy(nullable = true))

    assertThat(table)
      .isEqualTo(
        TableElement(
          parsedType = mockParsedType(typeName = ClassName(PACKAGE, "Book")),
          tableName = "books",
          artifactStem = "Book",
          declarationOrder = 0,
          options = setOf(TEMPORARY),
          construction = ModelConstruction(
            strategy = PRIMARY_CONSTRUCTOR,
            constructorParameters = listOf(idPath, titlePath, subtitlePath),
            defaultableParameters = emptySet()
          ),
          properties = listOf(
            ColumnPropertyElement(
              column = ColumnElement(
                access = PropertyAccess(
                  path = idPath,
                  isMutable = false
                ),
                deserializedType = stringType,
                isNullable = false,
                columnName = "book_id",
                isSchemaNullable = false,
                defaultValue = "''",
                transformer = null,
                relationship = null,
                id = IdElement(
                  autoIncrementMode = DISABLED,
                  isAutoIncrement = false,
                  canAssignGeneratedId = false
                ),
                isUnique = false,
                index = null,
                belongsToIndex = null,
                embeddedPrefixes = emptyList()
              )
            ),
            ColumnPropertyElement(
              column = ColumnElement(
                access = PropertyAccess(
                  path = titlePath,
                  isMutable = false
                ),
                deserializedType = nullableStringType,
                isNullable = true,
                columnName = "title",
                isSchemaNullable = true,
                defaultValue = "NULL",
                transformer = null,
                relationship = null,
                id = null,
                isUnique = true,
                index = IndexElement(
                  name = "title_lookup",
                  isUnique = true
                ),
                belongsToIndex = "books_title",
                embeddedPrefixes = emptyList()
              )
            ),
            ColumnPropertyElement(
              column = ColumnElement(
                access = PropertyAccess(
                  path = subtitlePath,
                  isMutable = false
                ),
                deserializedType = stringType,
                isNullable = false,
                columnName = "subtitle",
                isSchemaNullable = false,
                defaultValue = "''",
                transformer = null,
                relationship = null,
                id = null,
                isUnique = false,
                index = null,
                belongsToIndex = "books_title",
                embeddedPrefixes = emptyList()
              )
            )
          )
        )
      )
    assertThat(environment.tableRoundElementsForCurrentRound.single().tableElement)
      .isSameInstanceAs(table)
  }

  @Test
  fun `collects complete embedded relationship value`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Owner(
              @Id(autoIncrement = false) val id: String,
              val name: String = ""
            )

            data class Metadata(
              @Column(
                handleRecursively = false,
                onDeleteCascade = true
              )
              val owner: Owner
            )

            @Table
            data class Project(
              @Embedded(prefix = "meta_") val metadata: Metadata?
            )
          """
        )
      )
      .isOk()
    val metadataPath = mockPropertyPath("metadata")
    val ownerPath = metadataPath.child("owner")
    val ownerIdPath = mockPropertyPath("id")
    val ownerType = mockParsedType(typeName = ClassName(PACKAGE, "Owner"))
    val stringType = mockParsedType(typeName = STRING)
    val relationship = RelationshipElement(
      referencedTableType = ownerType,
      referencedTableName = "owner",
      referencedIdProperty = ownerIdPath,
      referencedIdColumnName = "id",
      referencedIdType = stringType,
      referencedIdSerializedType = stringType,
      referencedIdTransformer = null,
      isHandledRecursively = false,
      onDeleteCascade = true,
      canConstructWithOnlyId = true
    )

    assertThat(compilation.environment.tableElements.values.single { table ->
      table.modelName == "Project"
    })
      .isEqualTo(
        TableElement(
          parsedType = mockParsedType(typeName = ClassName(PACKAGE, "Project")),
          tableName = "project",
          artifactStem = "Project",
          declarationOrder = 1,
          options = emptySet(),
          construction = ModelConstruction(
            strategy = PRIMARY_CONSTRUCTOR,
            constructorParameters = listOf(metadataPath),
            defaultableParameters = emptySet()
          ),
          properties = listOf(
            EmbeddedPropertyElement(
              access = PropertyAccess(
                path = metadataPath,
                isMutable = false
              ),
              deserializedType = mockParsedType(
                typeName = ClassName(PACKAGE, "Metadata").copy(nullable = true)
              ),
              isNullable = true,
              prefix = "meta_",
              cumulativePrefix = "meta_",
              construction = ModelConstruction(
                strategy = PRIMARY_CONSTRUCTOR,
                constructorParameters = listOf(ownerPath),
                defaultableParameters = emptySet()
              ),
              properties = listOf(
                ColumnPropertyElement(
                  column = ColumnElement(
                    access = PropertyAccess(
                      path = ownerPath,
                      isMutable = false
                    ),
                    deserializedType = ownerType,
                    isNullable = false,
                    columnName = "meta_owner",
                    isSchemaNullable = true,
                    defaultValue = "NULL",
                    transformer = null,
                    relationship = relationship,
                    id = null,
                    isUnique = false,
                    index = null,
                    belongsToIndex = null,
                    embeddedPrefixes = listOf("meta_")
                  )
                )
              )
            )
          )
        )
      )
  }

  @Test
  fun `collects valid tables while deferring unresolved tables across rounds`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MixedRoundTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ImmediateTable(val value: String)

            @Table
            data class DeferredTable(val value: GeneratedValue)
          """
        ),
        processingStepsFactory = { environment ->
          listOf(
            GeneratedTypeStep(environment),
            ModelCollectionStep(environment)
          )
        }
      )
      .isOk()
    val environment = compilation.environment
    val tables = environment.tableElements.values

    assertThat(environment.processingRounds).isAtLeast(2)
    assertThat(tables.map(TableElement::modelName))
      .containsExactly("ImmediateTable", "DeferredTable")
      .inOrder()
    assertThat(tables.any(TableElement::containsLiveKspSymbol)).isFalse()
  }

  @Test
  fun `reports focused collection diagnostics`() {
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
              @Id val first: Long,
              @Id val second: Long
            )
          """
        )
      )
      .assertCompilationError(
        "Table must declare at most one @Id",
        "DuplicateIds.first",
        "DuplicateIds.second"
      )
  }

  @Test
  fun `reports local collection diagnostics after an unrelated error`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IndependentDiagnostics.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DuplicateIds(
              @Id val first: Long,
              @Id val second: Long
            )

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
        "Table must declare at most one @Id",
        "DuplicateIds.first",
        "DuplicateIds.second",
        "Embedded value must define at least one persisted leaf column",
        "EmptyEmbeddedValue.details",
        "EmptyDetails"
      )
  }

  @Test
  fun `reports relationship cycles after an unrelated materialization error`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IndependentMaterializationDiagnostics.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class InvalidAutoIncrement(
              @Id(autoIncrement = true) val id: String
            )

            @Table
            data class Parent(
              @Id val id: Long,
              val child: Child
            )

            @Table
            data class Child(
              @Id val id: Long,
              val parent: Parent
            )
          """
        )
      )
      .assertCompilationError(
        "Explicit auto-increment requires an INTEGER-compatible ID",
        "InvalidAutoIncrement.id",
        "Table graph validation failed: Tables cannot have reference cycles.",
        "Parent-Child"
      )
  }

  @Test
  fun `reports artifact collisions across model packages`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "FirstSharedModel.kt",
          contents = """
            package $PACKAGE.first

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class SharedModel(val first: String)
          """
        ),
        SourceFile.kotlin(
          name = "SecondSharedModel.kt",
          contents = """
            package $PACKAGE.second

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class SharedModel(val second: String)
          """
        )
      )
      .assertCompilationError(
        "Cannot generate code for declarations",
        "their declaration paths map to the same generated name 'SharedModel'",
        "Rename one model or an enclosing class to make the generated names unique",
        "$PACKAGE.first.SharedModel",
        "$PACKAGE.second.SharedModel"
      )
  }

  @Test
  fun `derives distinct default table names for nested declaration paths`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NestedTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            class FirstContainer {
              @Table
              class NestedTable(val firstValue: String)
            }

            class SecondContainer {
              @Table
              class NestedTable(val secondValue: String)
            }
          """
        )
      )
      .isOk()

    assertThat(compilation.environment.tableElements.values.map(TableElement::tableName))
      .containsExactly(
        "first_container_nested_table",
        "second_container_nested_table"
      )
      .inOrder()
  }

  @Test
  fun `reports duplicate effective SQL table names`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "FirstTable.kt",
          contents = """
            package $PACKAGE.first

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("shared_table")
            data class FirstTable(val first: String)
          """
        ),
        SourceFile.kotlin(
          name = "SecondTable.kt",
          contents = """
            package $PACKAGE.second

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("shared_table")
            data class SecondTable(val second: String)
          """
        )
      )
      .assertCompilationError(
        "SQL table name 'shared_table' is ambiguous",
        "$PACKAGE.first.FirstTable",
        "$PACKAGE.second.SecondTable"
      )
  }

  @Test
  fun `retains relationship and transformer originating files`() {
    val originatingFiles = mutableMapOf<String, OriginatingFilesSnapshot>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Email.kt",
          contents = """
            package $PACKAGE

            data class Email(val value: String)
          """
        ),
        SourceFile.kotlin(
          name = "EmailTransformer.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            @ObjectToDbValue
            fun emailToString(value: Email): String = value.value

            @DbValueToObject
            fun stringToEmail(value: String): Email = Email(value)
          """
        ),
        SourceFile.kotlin(
          name = "Owner.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Owner(@Id val id: Email)
          """
        ),
        SourceFile.kotlin(
          name = "Project.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Project(val owner: Owner)
          """
        ),
        processingStepsFactory = { environment ->
          modelProcessingSteps(environment) + OriginRecordingStep(
            environment = environment,
            originatingFiles = originatingFiles
          )
        }
      )
      .isOk()

    assertThat(originatingFiles.getValue("Project"))
      .isEqualTo(
        OriginatingFilesSnapshot(
          files = setOf(
            "Project.kt",
            "Owner.kt",
            "Email.kt",
            "EmailTransformer.kt"
          ),
          isComplete = true
        )
      )
  }

  @Test
  fun `retains transitive recursive relationship originating files`() {
    val originatingFiles = mutableMapOf<String, OriginatingFilesSnapshot>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Root.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Root(@Id val id: Long)
          """
        ),
        SourceFile.kotlin(
          name = "Branch.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Branch(
              @Id val id: Long,
              val root: Root
            )
          """
        ),
        SourceFile.kotlin(
          name = "Leaf.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Leaf(
              @Id val id: Long,
              val branch: Branch
            )
          """
        ),
        processingStepsFactory = { environment ->
          modelProcessingSteps(environment) + OriginRecordingStep(
            environment = environment,
            originatingFiles = originatingFiles
          )
        }
      )
      .isOk()

    assertThat(originatingFiles.getValue("Leaf"))
      .isEqualTo(
        OriginatingFilesSnapshot(
          files = setOf(
            "Leaf.kt",
            "Branch.kt",
            "Root.kt"
          ),
          isComplete = true
        )
      )
  }

  @Test
  fun `marks originating files incomplete when transformer was collected in an earlier round`() {
    val originatingFiles = mutableMapOf<String, OriginatingFilesSnapshot>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Email.kt",
          contents = """
            package $PACKAGE

            data class Email(val value: String)
          """
        ),
        SourceFile.kotlin(
          name = "EmailTransformer.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            @ObjectToDbValue
            fun emailToString(value: Email): String = value.value

            @DbValueToObject
            fun stringToEmail(value: String): Email = Email(value)
          """
        ),
        SourceFile.kotlin(
          name = "DeferredTransformedTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DeferredTransformedTable(
              val email: Email,
              val generatedValue: GeneratedValue
            )
          """
        ),
        processingStepsFactory = { environment ->
          listOf(
            TransformerCollectionStep(environment),
            GeneratedTypeStep(environment),
            ModelCollectionStep(environment),
            OriginRecordingStep(
              environment = environment,
              originatingFiles = originatingFiles
            )
          )
        }
      )
      .isOk()

    assertThat(originatingFiles.getValue("DeferredTransformedTable"))
      .isEqualTo(
        OriginatingFilesSnapshot(
          files = setOf(
            "DeferredTransformedTable.kt",
            "Email.kt"
          ),
          isComplete = false
        )
      )
  }
}

private data class OriginatingFilesSnapshot(
  val files: Set<String>,
  val isComplete: Boolean
) {
  companion object {
    fun from(original: OriginatingFiles) = OriginatingFilesSnapshot(
      files = original.files
        .mapTo(
          destination = linkedSetOf(),
          transform = KSFile::fileName
        ),
      isComplete = original.isComplete
    )
  }
}

private class OriginRecordingStep(
  private val environment: Environment,
  private val originatingFiles: MutableMap<String, OriginatingFilesSnapshot>
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    environment.tableRoundElementsForCurrentRound.forEach { table ->
      originatingFiles[table.tableElement.modelName] = OriginatingFilesSnapshot.from(table.originatingFiles)
    }
    return Continue
  }
}

private class GeneratedTypeStep(
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
        fileName = "GeneratedValue"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
          package $PACKAGE

          typealias GeneratedValue = String
          """.trimIndent()
        )
      }
    return Continue
  }
}

private fun Any.containsLiveKspSymbol(): Boolean {
  val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

  fun contains(value: Any?): Boolean = when {
    value == null -> false
    value is KSNode -> true
    value is Iterable<*> -> value.any(::contains)
    value is Map<*, *> -> value.entries.any { entry ->
      contains(entry.key) || contains(entry.value)
    }
    !value.javaClass.name.startsWith("com.siimkinks.sqlitemagic") -> false
    !visited.add(value) -> false
    else -> value.javaClass.declaredFields.any { field ->
      field.isAccessible = true
      contains(field.get(value))
    }
  }
  return contains(this)
}
