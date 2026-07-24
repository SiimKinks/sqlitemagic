package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelIdentityRelationshipContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `generates only safe operations for a table without an ID or non-null unique key`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AuditEvent.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class AuditEvent(
              val message: String,
              val createdAt: Long,
              @Unique val nullableExternalKey: String?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AuditEvent_Dao.kt",
        "_AuditEvent.kt"
      )
      .withGeneratedSource("SqliteMagic_AuditEvent_Dao.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "fun getId(",
          "fun setId(",
          "newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("SqliteMagic_AuditEvent_Handler.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "_id",
          "UpdateBuilder",
          "BulkUpdateBuilder",
          "PersistBuilder",
          "BulkPersistBuilder",
          "DeleteBuilder",
          "BulkDeleteBuilder"
        )
      }
      .withGeneratedSource("_AuditEvent.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun AuditEvent.insert()",
          "object AuditEvents",
          "fun deleteTable()",
          "fun insert(o: Iterable<AuditEvent>)"
        )
        generatedSource.assertDoesNotContain(
          "fun AuditEvent.update()",
          "fun AuditEvent.persist()",
          "fun AuditEvent.delete()",
          "fun update(o:",
          "fun persist(o:",
          "fun delete(o:"
        )
      }
  }

  @Test
  fun `requires an explicit unique column at no-ID identity operation terminals`() {
    SqliteMagicCompilation
      .compile(sluggedNoteSource())
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_SluggedNote_Handler.kt",
        "_SluggedNote.kt"
      )
      .withGeneratedSource("SqliteMagic_SluggedNote_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "slug TEXT UNIQUE",
          "external_key TEXT UNIQUE",
          "UpdateBuilder",
          "BulkUpdateBuilder",
          "PersistBuilder",
          "BulkPersistBuilder",
          "DeleteBuilder",
          "BulkDeleteBuilder",
          "EntityUpdateByColumnBuilder",
          "EntityBulkUpdateByColumnBuilder",
          "EntityPersistByColumnBuilder",
          "EntityBulkPersistByColumnBuilder",
          "EntityDeleteByColumnBuilder",
          "EntityBulkDeleteByColumnBuilder",
          "fun execute(byColumn:",
          "fun observe(byColumn:",
          "UniqueColumn<",
          "EntityPersistResult",
          "EntityPersistResult.Inserted",
          "rowId",
          "EntityPersistResult.Updated",
          "EntityPersistResult.Ignored",
          "Single<EntityPersistResult>"
        )
      }
      .withGeneratedSource("_SluggedNote.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun SluggedNote.update()",
          "fun SluggedNote.persist()",
          "fun SluggedNote.delete()",
          "fun update(o: Iterable<SluggedNote>)",
          "fun persist(o: Iterable<SluggedNote>)",
          "fun delete(o: Collection<SluggedNote>)"
        )
      }
  }

  @Test
  fun `does not expose zero-argument terminals for no-ID identity operations`() {
    SqliteMagicCompilation
      .compile(
        sluggedNoteSource(),
        SourceFile.kotlin(
          name = "ZeroArgumentIdentityOperations.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.delete
            import com.siimkinks.sqlitemagic.persist
            import com.siimkinks.sqlitemagic.update

            fun updateExecute(note: SluggedNote) = note.update().execute()
            fun updateObserve(note: SluggedNote) = note.update().observe()
            fun persistExecute(note: SluggedNote) = note.persist().execute()
            fun persistObserve(note: SluggedNote) = note.persist().observe()
            fun deleteExecute(note: SluggedNote) = note.delete().execute()
            fun deleteObserve(note: SluggedNote) = note.delete().observe()
          """
        )
      )
      .assertCompilationError("No value passed for parameter 'byColumn'")
  }

  @Test
  fun `accepts an explicit generated unique column at no-ID identity operation terminals`() {
    SqliteMagicCompilation
      .compile(
        sluggedNoteSource(),
        SourceFile.kotlin(
          name = "ExplicitIdentityOperations.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.delete
            import com.siimkinks.sqlitemagic.persist
            import com.siimkinks.sqlitemagic.update

            private val slugColumn = SluggedNoteTable.SLUGGED_NOTE.SLUG

            fun updateBySlug(note: SluggedNote) =
                note.update().execute(byColumn = slugColumn)

            fun persistBySlug(note: SluggedNote) =
                note.persist().observe(byColumn = slugColumn)

            fun deleteBySlug(note: SluggedNote) =
                note.delete().execute(byColumn = slugColumn)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SluggedNoteTable.kt",
        "_SluggedNote.kt"
      )
  }

  @Test
  fun `resolves omitted auto-increment mode from the raw annotation`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AutomaticModels.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class SequenceId(val value: Long)

            @ObjectToDbValue
            fun sequenceIdToLong(value: SequenceId): Long = value.value

            @DbValueToObject
            fun longToSequenceId(value: Long): SequenceId = SequenceId(value)

            @Table
            data class AutomaticLong(@Id val id: Long)

            @Table
            data class AutomaticString(@Id val id: String)

            @Table
            data class AutomaticTransformed(@Id val id: SequenceId)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AutomaticLong_Handler.kt",
        "SqliteMagic_AutomaticString_Handler.kt",
        "SqliteMagic_AutomaticTransformed_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_AutomaticLong_Handler.kt") { generatedSource ->
        generatedSource.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticString_Handler.kt") { generatedSource ->
        generatedSource.assertContains("TEXT PRIMARY KEY")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticTransformed_Handler.kt") { generatedSource ->
        generatedSource.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
  }

  @Test
  fun `resolves explicit auto-increment mode from the raw annotation`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DisabledModels.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class DisabledLong(
              @Id(autoIncrement = false) val id: Long
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_DisabledLong_Handler.kt")
      .withGeneratedSource("SqliteMagic_DisabledLong_Handler.kt") { generatedSource ->
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `enables explicitly requested auto-increment for a compatible ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EnabledAutoIncrement.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class EnabledAutoIncrement(
              @Id(autoIncrement = true) val id: Long
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_EnabledAutoIncrement_Handler.kt")
      .withGeneratedSource("SqliteMagic_EnabledAutoIncrement_Handler.kt") { generatedSource ->
        generatedSource.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
  }

  @Test
  fun `rejects explicitly enabled auto-increment for an incompatible ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InvalidAutoIncrement.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class InvalidAutoIncrement(
              @Id(autoIncrement = true) val id: String
            )
          """
        )
      )
      .assertCompilationError(
        "Explicit auto-increment requires an INTEGER-compatible ID",
        "InvalidAutoIncrement.id"
      )
  }

  @Test
  fun `preserves transformed IDs and relationship key types`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TypedRelationships.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class AccountId(val value: String)

            @ObjectToDbValue
            fun accountIdToString(value: AccountId): String = value.value

            @DbValueToObject
            fun stringToAccountId(value: String): AccountId = AccountId(value)

            @Table
            data class Author(
              @Id val id: String,
              val name: String = ""
            )

            @Table
            data class Account(
              @Id val id: AccountId,
              val label: String = ""
            )

            @Table
            data class Publisher(
              @Id val id: Long,
              val name: String = ""
            )

            @Table
            data class Article(
              @Id val id: String,
              @Column(handleRecursively = false) val author: Author,
              @Column(handleRecursively = false) val account: Account,
              @Column(handleRecursively = false) val publisher: Publisher
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Account_Dao.kt",
        "SqliteMagic_Article_Dao.kt",
        "SqliteMagic_Article_Handler.kt",
        "ArticleTable.kt"
      )
      .withGeneratedSource("SqliteMagic_Article_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "author TEXT",
          "account TEXT",
          "publisher INTEGER"
        )
      }
      .withGeneratedSource("SqliteMagic_Article_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "author.id",
          "accountIdToString",
          "stringToAccountId",
          "publisher.id"
        )
        generatedSource.assertDoesNotContain(
          "bindLong",
          "Long.toString"
        )
      }
      .withGeneratedSource("ArticleTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<String",
          "ComplexColumn<AccountId",
          "ComplexNumericColumn<Long"
        )
      }
  }

  @Test
  fun `generates ID-only reconstruction only when constructor defaults permit it`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IdOnlyReconstruction.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class IdOnlyConstructible(
              @Id val id: String,
              val value: String = ""
            )

            @Table
            data class IdOnlyInconstructible(
              @Id val id: String,
              val requiredValue: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_IdOnlyConstructible_Dao.kt",
        "SqliteMagic_IdOnlyInconstructible_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_IdOnlyConstructible_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun newInstanceWithOnlyId(id: String): IdOnlyConstructible",
          "IdOnlyConstructible(id = id)"
        )
      }
      .withGeneratedSource("SqliteMagic_IdOnlyInconstructible_Dao.kt") { generatedSource ->
        generatedSource.assertDoesNotContain("newInstanceWithOnlyId")
      }
  }

  @Test
  fun `generates recursive relationship persistence retrieval and query graph behavior`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RecursiveRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Team(
              @Id val id: String,
              val name: String = ""
            )

            @Table
            data class TeamMember(
              @Id val id: String,
              @Column(handleRecursively = true) val team: Team
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TeamMember_Dao.kt",
        "SqliteMagic_TeamMember_Handler.kt",
        "TeamMemberTable.kt"
      )
      .withGeneratedSource("SqliteMagic_TeamMember_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "team TEXT",
          "SqliteMagic_Team_Handler",
          "callInternalInsertsOnComplexColumns",
          "callInternalUpdatesOnComplexColumns",
          "callInternalPersistsOnComplexColumns"
        )
      }
      .withGeneratedSource("SqliteMagic_TeamMember_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fullObjectFromCursorPosition",
          "SqliteMagic_Team_Dao.fullObjectFromCursorPosition"
        )
      }
      .withGeneratedSource("TeamMemberTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "addDeepQueryParts",
          "TeamTable.TEAM"
        )
      }
  }

  @Test
  fun `allows a relationship cycle when a non-recursive edge breaks the persistence graph`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "BrokenRecursiveCycle.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RecursiveParent(
              @Id val id: String,
              val child: RecursiveChild? = null
            )

            @Table
            data class RecursiveChild(
              @Id val id: String,
              @Column(handleRecursively = false) val parent: RecursiveParent
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RecursiveParent_Handler.kt",
        "SqliteMagic_RecursiveChild_Handler.kt"
      )
  }

  @Test
  fun `rejects shallow relationships that cannot reconstruct the target from only its ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InconstructibleShallowRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RequiredParent(
              @Id val id: String,
              val requiredValue: String
            )

            @Table
            data class ShallowChild(
              @Id val id: String,
              @Column(handleRecursively = false) val parent: RequiredParent
            )
          """
        )
      )
      .assertCompilationError(
        "A non-recursive relationship target must be constructible from only its @Id",
        "ShallowChild.parent",
        "RequiredParent"
      )
  }

  @Test
  fun `preserves nullable shallow relationship access and reconstruction`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class OptionalParent(
              @Id val id: String,
              val value: String = ""
            )

            @Table
            data class OptionalChild(
              @Id val id: String,
              @Column(handleRecursively = false) val parent: OptionalParent?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_OptionalChild_Dao.kt",
        "SqliteMagic_OptionalChild_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_OptionalChild_Handler.kt") { generatedSource ->
        generatedSource.assertContains("parent TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_OptionalChild_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "parent?.id",
          "cursor.isNull",
          "parent = null",
          "newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `generates cascade schema and recursive operation table triggers`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CascadeRelationships.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RootEntity(@Id val id: String)

            @Table
            data class BranchEntity(
              @Id val id: String,
              @Column(onDeleteCascade = true) val root: RootEntity
            )

            @Table
            data class LeafEntity(
              @Id val id: String,
              @Column(onDeleteCascade = true) val branch: BranchEntity
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_LeafEntity_Handler.kt")
      .withGeneratedSource("SqliteMagic_LeafEntity_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "branch TEXT REFERENCES branch_entity(id) ON DELETE CASCADE",
          "sendTableTriggers(",
          "LeafEntityTable.LEAF_ENTITY.name",
          "BranchEntityTable.BRANCH_ENTITY.name",
          "RootEntityTable.ROOT_ENTITY.name"
        )
      }
  }

  @Test
  fun `rejects relationships to tables without an explicit ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MissingRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class UnidentifiedParent(val name: String)

            @Table
            data class Child(
              @Id val id: Long,
              val parent: UnidentifiedParent
            )
          """
        )
      )
      .assertCompilationError(
        "Relationship target must declare an explicit @Id",
        "Child.parent",
        "UnidentifiedParent"
      )
  }

  @Test
  fun `rejects recursive relationship cycles`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RecursiveRelationships.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

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
        "VALIDATION ERROR:",
        "Table graph validation failed: Tables cannot have reference cycles.",
        "Found cycles:",
        "Parent-Child",
        "Possible fix: remove some complex columns or annotate them with @Column(handleRecursively = false)"
      )
  }

  private fun sluggedNoteSource() = SourceFile.kotlin(
    name = "SluggedNote.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.Unique

      @Table
      data class SluggedNote(
        @Unique val slug: String,
        @Unique val externalKey: String,
        val body: String
      )
    """
  )
}
