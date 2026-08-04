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
          "bindNotNull",
          "fullObjectFromCursorPosition",
          "fun getId(",
          "fun setId(",
          "generatedRelationshipIds",
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
          "execute(byColumn: C)",
          "observe(byColumn: C)",
          "Unique<",
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
          "EntityUpdateByColumnBuilder<SluggedNote>",
          "fun SluggedNote.persist()",
          "EntityPersistByColumnBuilder<SluggedNote>",
          "fun SluggedNote.delete()",
          "EntityDeleteByColumnBuilder<SluggedNote>",
          "fun update(o: Iterable<SluggedNote>)",
          "EntityBulkUpdateByColumnBuilder<SluggedNote>",
          "fun persist(o: Iterable<SluggedNote>)",
          "EntityBulkPersistByColumnBuilder<SluggedNote>",
          "fun delete(o: Collection<SluggedNote>)",
          "EntityBulkDeleteByColumnBuilder<SluggedNote>"
        )
      }
  }

  @Test
  fun `generates an eligible unique relationship column`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "UniqueRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class UniqueRelationshipTarget(
              @Id val id: String
            )

            @Table
            data class UniqueRelationshipOwner(
              @Unique
              @Column(handleRecursively = false)
              val target: UniqueRelationshipTarget,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_UniqueRelationshipOwner_TargetColumn.kt",
        "SqliteMagic_UniqueRelationshipOwner_Handler.kt"
      )
      .withGeneratedSource("SqliteMagic_UniqueRelationshipOwner_TargetColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<String, String",
          "Unique<N>"
        )
      }
      .withGeneratedSource("SqliteMagic_UniqueRelationshipOwner_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityUpdateByColumnBuilder",
          "execute(byColumn: C)"
        )
      }
  }

  @Test
  fun `does not treat a transformer with nullable storage output as an entity key`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableSerializedKey.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class MaybeKey(val value: String)

            @ObjectToDbValue
            fun maybeKeyToString(value: MaybeKey): String? = value.value

            @DbValueToObject
            fun stringToMaybeKey(value: String?): MaybeKey = MaybeKey(value.orEmpty())

            @Table
            data class NullableSerializedKey(
              @Unique val key: MaybeKey,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableSerializedKey_Handler.kt")
      .withGeneratedSource("SqliteMagic_NullableSerializedKey_Handler.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "UpdateBuilder",
          "PersistBuilder",
          "DeleteBuilder"
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
  fun `accepts a unique column at no-ID identity terminals`() {
    SqliteMagicCompilation
      .compile(
        sluggedNoteSource(),
        SourceFile.kotlin(
          name = "ExplicitIdentityOperations.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SluggedNoteTable
            import com.siimkinks.sqlitemagic.delete
            import com.siimkinks.sqlitemagic.persist
            import com.siimkinks.sqlitemagic.update

            private val slugColumn = SluggedNoteTable.SLUGGED_NOTE.SLUG
            private val aliasedSlugColumn = SluggedNoteTable.SLUGGED_NOTE
              .`as`("note_alias")
              .SLUG

            fun updateBySlug(note: SluggedNote) =
                note.update().execute(slugColumn)

            fun persistBySlug(note: SluggedNote) =
                note.persist().observe(slugColumn)

            fun deleteBySlug(note: SluggedNote) =
                note.delete().execute(slugColumn)

            fun updateByAliasedSlug(note: SluggedNote) =
                note.update().execute(aliasedSlugColumn)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_SluggedNote_Dao.kt",
        "SluggedNoteTable.kt",
        "_SluggedNote.kt"
      )
      .withGeneratedSource("SqliteMagic_SluggedNote_Dao.kt") { generatedSource ->
        generatedSource.assertDoesNotContain("object SluggedNoteTable")
      }
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
  fun `does not automatically enable auto-increment for a relationship-backed ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AutomaticRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RelationshipKey(
              @Id(autoIncrement = false) val value: Int
            )

            @Table
            data class AutomaticRelationshipId(
              @Id val id: RelationshipKey
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_AutomaticRelationshipId_Handler.kt")
      .withGeneratedSource("SqliteMagic_AutomaticRelationshipId_Handler.kt") { generatedSource ->
        generatedSource.assertContains("id INTEGER PRIMARY KEY")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `rejects explicitly enabled auto-increment for a relationship-backed ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ExplicitRelationshipAutoIncrement.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ExplicitRelationshipKey(
              @Id(autoIncrement = false) val value: Int
            )

            @Table
            data class ExplicitRelationshipAutoIncrement(
              @Id(autoIncrement = true) val id: ExplicitRelationshipKey
            )
          """
        )
      )
      .assertCompilationError(
        "Explicit auto-increment requires an INTEGER-compatible ID",
        "ExplicitRelationshipAutoIncrement.id"
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
        "SqliteMagic_Article_AccountColumn.kt",
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
          "publisher.id",
          "bindString",
          "bindLong"
        )
        generatedSource.assertDoesNotContain("Long.toString")
      }
      .withGeneratedSource("ArticleTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<String",
          "SqliteMagic_Article_AccountColumn<Article",
          "ComplexNumericColumn<Long"
        )
      }
      .withGeneratedSource(
        "SqliteMagic_Article_AccountColumn.kt"
      ) { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<AccountId, AccountId",
          "accountIdToString",
          "stringToAccountId"
        )
      }
  }

  @Test
  fun `passes nullable transformed relationship storage values to the parser`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableTransformedRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class OptionalAccountId(val value: String)

            @ObjectToDbValue
            fun optionalAccountIdToString(value: OptionalAccountId): String? = value.value

            @DbValueToObject
            fun stringToOptionalAccountId(value: String?): OptionalAccountId =
              OptionalAccountId(value.orEmpty())

            @Table
            data class OptionalIdAccount(
              @Id val id: OptionalAccountId
            )

            @Table
            data class OptionalIdOwner(
              @Id val id: String,
              @Column(handleRecursively = false)
              val account: OptionalIdAccount
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_OptionalIdOwner_AccountColumn.kt")
      .withGeneratedSource("SqliteMagic_OptionalIdOwner_AccountColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "val dbValue = super.getFromCursor<String>(cursor)",
          "stringToOptionalAccountId(dbValue)"
        )
        generatedSource.assertDoesNotContain(
          "super.getFromCursor<String>(cursor) ?: return null"
        )
      }
  }

  @Test
  fun `serializes and reconstructs relationship-backed IDs`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RelationshipBackedId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class LeafId(
              @Id val value: String
            )

            @Table
            data class RelationshipId(
              @Id
              @Column(handleRecursively = false)
              val id: LeafId
            )

            @Table
            data class RelationshipOwner(
              @Id val ownerId: String,
              @Column(handleRecursively = false)
              val relationshipId: RelationshipId
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RelationshipOwner_Dao.kt",
        "SqliteMagic_RelationshipId_IdColumn.kt",
        "SqliteMagic_RelationshipOwner_RelationshipIdColumn.kt"
      )
      .withGeneratedSource("SqliteMagic_RelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "entity.relationshipId.id.`value`",
          "SqliteMagic_LeafId_Dao.newInstanceWithOnlyId",
          "SqliteMagic_RelationshipId_Dao.newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("SqliteMagic_RelationshipOwner_RelationshipIdColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<LeafId, LeafId",
          "`value`.`value`",
          "SqliteMagic_LeafId_Dao.newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("SqliteMagic_RelationshipId_IdColumn.kt") { generatedSource ->
        generatedSource.assertContains("Unique<N>")
      }
  }

  @Test
  fun `propagates nullable relationship-backed IDs through the declared ID boundary`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRelationshipBackedId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableRelationshipLeaf(
              @Id val value: String
            )

            @Table
            data class NullableRelationshipId(
              @Id
              @Column(handleRecursively = false)
              val id: NullableRelationshipLeaf?
            )

            @Table
            data class NullableRelationshipOwner(
              @Id val ownerId: String,
              @Column(handleRecursively = false)
              val target: NullableRelationshipId
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableRelationshipOwner_Dao.kt",
        "SqliteMagic_NullableRelationshipOwner_TargetColumn.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "SqliteMagic_NullableRelationshipId_Dao.newInstanceWithOnlyId(if (column1IsNull",
          "SqliteMagic_NullableRelationshipLeaf_Dao.newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableRelationshipOwner_TargetColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "dbValue?.let {",
          "SqliteMagic_NullableRelationshipLeaf_Dao.newInstanceWithOnlyId(it)"
        )
      }
  }

  @Test
  fun `does not pass nullable relationship storage into a non-null transformer input`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NonNullTransformedRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class RequiredAccountId(val value: String)

            @ObjectToDbValue
            fun requiredAccountIdToString(value: RequiredAccountId): String = value.value

            @DbValueToObject
            fun stringToRequiredAccountId(value: String): RequiredAccountId =
              RequiredAccountId(value)

            @Table
            data class RequiredAccount(
              @Id val id: RequiredAccountId?
            )

            @Table
            data class RequiredAccountOwner(
              @Id val ownerId: String,
              @Column(handleRecursively = false)
              val account: RequiredAccount
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_RequiredAccountOwner_AccountColumn.kt")
      .withGeneratedSource("SqliteMagic_RequiredAccountOwner_AccountColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "dbValue?.let {",
          "stringToRequiredAccountId(it)"
        )
      }
  }

  @Test
  fun `reconstructs an absent nullable relationship-backed ID as null`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRelationshipBackedTarget.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class NullableSourceRelationshipId(
              val value: String
            )

            @ObjectToDbValue
            fun nullableSourceRelationshipIdToString(value: NullableSourceRelationshipId): String? = value.value

            @DbValueToObject
            fun stringToNullableSourceRelationshipId(value: String?): NullableSourceRelationshipId =
              NullableSourceRelationshipId(value.orEmpty())

            @Table
            data class NullableSourceRelationshipLeaf(
              @Id val id: NullableSourceRelationshipId
            )

            @Table
            data class NullableSourceRelationshipTarget(
              @Id
              @Column(handleRecursively = false)
              val id: NullableSourceRelationshipLeaf
            )

            @Table
            data class NullableSourceRelationshipOwner(
              @Id val ownerId: String,
              @Column(handleRecursively = false)
              val target: NullableSourceRelationshipTarget?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableSourceRelationshipOwner_TargetColumn.kt")
      .withGeneratedSource("SqliteMagic_NullableSourceRelationshipOwner_TargetColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "dbValue?.let { SqliteMagic_NullableSourceRelationshipLeaf_Dao.newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `distinguishes nullable referenced IDs from an absent relationship path`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableReferencedId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableIdTarget(
              @Id val id: String?
            )

            @Table
            data class NonNullTargetOwner(
              @Id val id: String,
              @Column(handleRecursively = false)
              val target: NullableIdTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NonNullTargetOwner_Handler.kt",
        "SqliteMagic_NonNullTargetOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Handler.kt") { generatedSource ->
        generatedSource.assertContains("target TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "?: throw SQLException",
          "newInstanceWithOnlyId(if (column1IsNull"
        )
        generatedSource.assertDoesNotContain("target = null")
      }
  }

  @Test
  fun `advances recursive cursor offsets when a nullable referenced ID is absent`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRecursiveReferencedId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableRecursiveIdTarget(
              @Id val id: String?,
              val value: String = ""
            )

            @Table
            data class NonNullRecursiveTargetOwner(
              @Id val id: String,
              @Column(handleRecursively = true)
              val target: NullableRecursiveIdTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NonNullRecursiveTargetOwner_Dao.kt")
      .withGeneratedSource("SqliteMagic_NonNullRecursiveTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "columnOffset.value += 2",
          "SqliteMagic_NullableRecursiveIdTarget_Dao.newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `advances recursive cursor offsets when a nullable embedded graph is absent`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableEmbeddedRecursiveGraph.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class EmbeddedRecursiveTarget(
              @Id val id: String,
              val requiredValue: String
            )

            data class NullableEmbeddedGraph(
              @Column(handleRecursively = true)
              val target: EmbeddedRecursiveTarget?
            )

            @Table
            data class NullableEmbeddedGraphOwner(
              @Id val id: String,
              @Embedded val graph: NullableEmbeddedGraph?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableEmbeddedGraphOwner_Dao.kt")
      .withGeneratedSource("SqliteMagic_NullableEmbeddedGraphOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "graph = if",
          "columnOffset.value += 2",
          "target = if"
        )
      }
  }

  @Test
  fun `does not emit foreign keys or transitive delete triggers for shallow relationships`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ShallowCascade.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ShallowParent(@Id val id: String)

            @Table
            data class ShallowChild(
              @Id val id: String,
              @Column(
                handleRecursively = false,
                onDeleteCascade = true
              )
              val parent: ShallowParent
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_ShallowChild_Handler.kt")
      .withGeneratedSource("SqliteMagic_ShallowChild_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "parent TEXT DEFAULT ''",
          "sendOwnTableTrigger",
          "ShallowChildTable.SHALLOW_CHILD.name"
        )
        generatedSource.assertDoesNotContain(
          "REFERENCES shallow_parent(id)",
          "ON DELETE CASCADE",
          "ShallowParentTable.SHALLOW_PARENT.name"
        )
      }
  }

  @Test
  fun `forwards generated immutable recursive IDs to the parent insert`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ImmutableRecursiveId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ImmutableGeneratedChild(
              @Id val id: Long = 0L
            )

            @Table
            data class ImmutableGeneratedParent(
              @Id val id: String,
              @Column(handleRecursively = true)
              val child: ImmutableGeneratedChild
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ImmutableGeneratedParent_Handler.kt",
        "SqliteMagic_ImmutableGeneratedParent_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "generatedRelationshipIds[\"child\"] = it",
          "bindToInsertStatement(statement, entity, generatedRelationshipIds)",
          "bindNotNull(entity, bindValues, generatedRelationshipIds)"
        )
      }
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "generatedRelationshipIds[\"child\"] ?: entity.child.id",
          "statement.bindLong"
        )
      }
  }

  @Test
  fun `uses affected-row insert results for WITHOUT ROWID models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NaturalKey.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption

            @Table(options = [TableOption.WITHOUT_ROWID])
            data class NaturalKey(
              @Id val id: String,
              val optionalValue: String?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NaturalKey_Handler.kt")
      .withGeneratedSource("SqliteMagic_NaturalKey_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityInsertResult.Inserted(null)",
          "VariableArgsOperationHelper",
          "bindValues",
          "statement.executeUpdateDelete()",
          "OperationHelper.Op.INSERT"
        )
        generatedSource.assertDoesNotContain(
          "executeWithoutRowIdInsert",
          "executeInsert()",
          "getWritableDatabase().insert("
        )
      }
  }

  @Test
  fun `persists a nullable missing ID through the insert path`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableIdentity.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableIdentity(
              @Id(autoIncrement = false) val id: String?,
              val value: String
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableIdentity_Handler.kt")
      .withGeneratedSource("SqliteMagic_NullableIdentity_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "if (!hasIdentityValue(entity, selectedByColumn))",
          "return entity.id != null",
          "val insertResult"
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
          "callInternalPersistsOnComplexColumns",
          "builder.withoutTableTriggers()",
          "var processed = false",
          "processed && conflictAlgorithm == SQLiteDatabase.CONFLICT_IGNORE",
          "rowId != -1L || conflictAlgorithm == SQLiteDatabase.CONFLICT_IGNORE",
          "TeamTable.TEAM"
        )
      }
      .withGeneratedSource("SqliteMagic_TeamMember_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fullObjectFromCursorPosition",
          "SqliteMagic_Team_Dao.shallowObjectFromCursorPosition"
        )
        generatedSource.assertDoesNotContain("SqliteMagic_Team_Dao.fullObjectFromCursorPosition")
      }
      .withGeneratedSource("TeamMemberTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "addDeepQueryParts",
          "queryDeep ->",
          "checkNotNull(",
          "SqliteMagic_TeamMember_Dao::fullObjectFromCursorPosition",
          "SqliteMagic_TeamMember_Dao::shallowObjectFromCursorPosition"
        )
        generatedSource.assertDoesNotContain("requireNotNull(")
      }
  }

  @Test
  fun `generates minimal shallow joins and shares cursor offsets for required recursive targets`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RequiredRecursiveTarget.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class ShallowLeaf(
              @Id val id: String
            )

            @Table
            data class RequiredRecursiveTarget(
              @Id val id: String,
              val requiredValue: String,
              @Column(handleRecursively = true)
              val leaf: ShallowLeaf
            )

            @Table
            data class RecursiveTargetOwner(
              @Id val id: String,
              val label: String,
              @Column(handleRecursively = true)
              val target: RequiredRecursiveTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "RecursiveTargetOwnerTable.kt",
        "SqliteMagic_RecursiveTargetOwner_Handler.kt",
        "SqliteMagic_RecursiveTargetOwner_Dao.kt",
        "SqliteMagic_RequiredRecursiveTarget_Dao.kt"
      )
      .withGeneratedSource("RecursiveTargetOwnerTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "addShallowQueryParts",
          "SqliteMagic_RecursiveTargetOwner_Handler.addShallowQueryParts"
        )
      }
      .withGeneratedSource("SqliteMagic_RecursiveTargetOwner_Handler.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun addShallowQueryPartsInternal",
          "JoinClause.indexOf",
          "userJoin.tableNameInQuery()"
        )
        generatedSource.assertDoesNotContain(
          "SqliteMagic_ShallowLeaf_Handler"
        )
      }
      .withGeneratedSource("SqliteMagic_RecursiveTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "columnOffset.value += 3",
          "SqliteMagic_RequiredRecursiveTarget_Dao.shallowObjectFromCursorPosition(cursor, columnOffset)"
        )
      }
      .withGeneratedSource("SqliteMagic_RequiredRecursiveTarget_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "leaf = SqliteMagic_ShallowLeaf_Dao.newInstanceWithOnlyId"
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
  fun `rejects recursive relationships with nullable IDs that cannot reconstruct the target`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InconstructibleNullableIdRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableIdTarget(
              @Id val id: String?,
              val requiredValue: String
            )

            @Table
            data class RecursiveNullableIdOwner(
              @Id val id: String,
              @Column(handleRecursively = true) val target: NullableIdTarget
            )
          """
        )
      )
      .assertCompilationError(
        "A recursive relationship with a nullable target @Id must be constructible from only that @Id",
        "RecursiveNullableIdOwner.target"
      )
  }

  @Test
  fun `rejects relationship-backed IDs whose target cannot be reconstructed from its ID`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InconstructibleRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RelationshipIdLeaf(
              @Id val id: String,
              val requiredValue: String
            )

            @Table
            data class RelationshipIdTarget(
              @Id
              @Column(handleRecursively = true)
              val id: RelationshipIdLeaf
            )

            @Table
            data class RelationshipIdOwner(
              @Id val id: String,
              @Column(handleRecursively = true)
              val target: RelationshipIdTarget
            )
          """
        )
      )
      .assertCompilationError(
        "A relationship-backed @Id target must be constructible from only its own @Id",
        "RelationshipIdOwner.target"
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
          "parent = if (",
          "null else",
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
          "branch TEXT DEFAULT '' REFERENCES branch_entity(id) ON DELETE CASCADE",
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
