package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
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
        "SqliteMagic_AuditEvent_Adapter.kt",
        "_AuditEvent.kt"
      )
      .withGeneratedSource("SqliteMagic_AuditEvent_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "generatedRelationshipIds: Map<String, Long>"
        )
        generatedSource.assertDoesNotContain(
          "bindNotNull",
          "fullObjectFromCursorPosition",
          "fun getId(",
          "fun setId(",
          "newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("SqliteMagic_AuditEvent_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_AuditEvent_Adapter",
          "EntityAdapter<AuditEvent",
          "EntityStatementBinder<AuditEvent> by SqliteMagic_AuditEvent_Dao"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "bindNotNull",
          "identity(",
          "updateStatementSql(",
          "defaultIdentityColumn"
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
        "SqliteMagic_SluggedNote_Adapter.kt",
        "_SluggedNote.kt"
      )
      .withGeneratedSource("SqliteMagic_SluggedNote_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "slug TEXT UNIQUE",
          "external_key TEXT UNIQUE",
          "EntityIdentityAdapter<SluggedNote>",
          "EntityIdentityStatementBinder<SluggedNote> by SqliteMagic_SluggedNote_Dao",
          "override fun identity(",
          "override fun hasIdentityValue(",
          "override fun updateStatementSql(",
          "requireNotNull(entity.slug)",
          "SluggedNoteTable.SLUGGED_NOTE.SLUG",
          "SluggedNoteTable.SLUGGED_NOTE.EXTERNAL_KEY"
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
        "SqliteMagic_UniqueRelationshipOwner_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_UniqueRelationshipOwner_TargetColumn.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<String, String, CharSequence",
          "Unique<N>"
        )
      }
      .withGeneratedSource("SqliteMagic_UniqueRelationshipOwner_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityIdentityAdapter<UniqueRelationshipOwner>",
          "override fun identity(",
          "override fun updateStatementSql(",
          "UniqueRelationshipOwnerTable.UNIQUE_RELATIONSHIP_OWNER.TARGET"
        )
      }
  }

  @Test
  fun `uses CharSequence equivalent type for a recursive plain text relationship`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RecursivePlainTextRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class RecursivePlainTextTarget(
              @Id val id: String
            )

            @Table
            data class RecursivePlainTextOwner(
              @Id val id: String,
              @Column(handleRecursively = true)
              val target: RecursivePlainTextTarget
            )
          """
        )
      )
      .isOk()
      .withGeneratedSource("RecursivePlainTextOwnerTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "ComplexColumn<String, String, CharSequence"
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
      .assertGeneratedSources("SqliteMagic_NullableSerializedKey_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NullableSerializedKey_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "internal object SqliteMagic_NullableSerializedKey_Adapter",
          "EntityAdapter<NullableSerializedKey>",
          "EntityStatementBinder<NullableSerializedKey> by SqliteMagic_NullableSerializedKey_Dao"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement("
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
        "SqliteMagic_AutomaticLong_Adapter.kt",
        "SqliteMagic_AutomaticString_Adapter.kt",
        "SqliteMagic_AutomaticTransformed_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_AutomaticLong_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticString_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("TEXT PRIMARY KEY")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticTransformed_Adapter.kt") { generatedSource ->
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
      .assertGeneratedSources("SqliteMagic_DisabledLong_Adapter.kt")
      .withGeneratedSource("SqliteMagic_DisabledLong_Adapter.kt") { generatedSource ->
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
      .assertGeneratedSources("SqliteMagic_EnabledAutoIncrement_Adapter.kt")
      .withGeneratedSource("SqliteMagic_EnabledAutoIncrement_Adapter.kt") { generatedSource ->
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
      .assertGeneratedSources("SqliteMagic_AutomaticRelationshipId_Adapter.kt")
      .withGeneratedSource("SqliteMagic_AutomaticRelationshipId_Adapter.kt") { generatedSource ->
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
        "SqliteMagic_Article_Adapter.kt",
        "SqliteMagic_Article_AccountColumn.kt",
        "ArticleTable.kt"
      )
      .withGeneratedSource("SqliteMagic_Article_Adapter.kt") { generatedSource ->
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
          "SqliteMagic_NullableRelationshipId_Dao.newInstanceWithOnlyId(cursor.getString(thisTableOffset + 1)",
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
        "SqliteMagic_NonNullTargetOwner_Adapter.kt",
        "SqliteMagic_NonNullTargetOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("target TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          """Relationship \"target\" resolved to a NULL ID""",
          "OperationFailedException",
          "?: throw SQLException",
          "Required relationship",
          "had a NULL ID"
        )
        generatedSource.assertDoesNotContain(
          "newInstanceWithOnlyId(if (column1IsNull",
          "statement.bindNull"
        )
      }
  }

  @Test
  fun `advances recursive cursor offsets before throwing for an absent required recursive ID`() {
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
          """Required recursive relationship \"target\" had a NULL ID"""
        )
      }
  }

  @Test
  fun `reconstructs a nullable recursive relationship as null when its ID is absent`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRecursiveRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class NullableRecursiveRelationshipTarget(
              @Id val id: String?,
              val requiredValue: String
            )

            @Table
            data class NullableRecursiveRelationshipOwner(
              @Id val id: String,
              @Column(handleRecursively = true)
              val target: NullableRecursiveRelationshipTarget?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableRecursiveRelationshipOwner_Dao.kt")
      .withGeneratedSource("SqliteMagic_NullableRecursiveRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "columnOffset.value += 2",
          "target = if (column1IsNull)",
          "null"
        )
        generatedSource.assertDoesNotContain(
          "Required recursive relationship",
          "NullableRecursiveRelationshipTarget_Dao.newInstanceWithOnlyId"
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
      .assertGeneratedSources("SqliteMagic_ShallowChild_Adapter.kt")
      .withGeneratedSource("SqliteMagic_ShallowChild_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "parent TEXT DEFAULT ''",
          "EntityDefaultIdentityAdapter<ShallowChild>",
          "EntityIdentityStatementBinder<ShallowChild> by SqliteMagic_ShallowChild_Dao",
          "override fun identity("
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate(",
          "REFERENCES shallow_parent(id)",
          "ON DELETE CASCADE",
          "ShallowParentTable.SHALLOW_PARENT.name"
        )
      }
  }

  @Test
  fun `allows temporary relationships to persistent tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TemporaryToPersistentRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table
            data class PersistentOwner(@Id val id: String)

            @Table(value = "temporary_entries", options = [TEMPORARY])
            data class TemporaryEntry(
              @Id val id: String,
              @Column(handleRecursively = false) val owner: PersistentOwner
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_TemporaryEntry_Adapter.kt")
  }

  @Test
  fun `rejects persistent relationships to temporary tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "PersistentToTemporaryRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table(value = "temporary_owners", options = [TEMPORARY])
            data class TemporaryOwner(@Id val id: String)

            @Table
            data class PersistentEntry(
              @Id val id: String,
              val owner: TemporaryOwner
            )
          """
        )
      )
      .assertCompilationError(
        "Persistent table relationships cannot target temporary tables",
        "PersistentEntry.owner",
        "TemporaryOwner"
      )
  }

  @Test
  fun `rejects cascade relationships across persistent and temporary tables`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TemporaryCascadeRelationship.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table
            data class PersistentOwner(@Id val id: String)

            @Table(value = "temporary_entries", options = [TEMPORARY])
            data class TemporaryEntry(
              @Id val id: String,
              @Column(onDeleteCascade = true) val owner: PersistentOwner
            )
          """
        )
      )
      .assertCompilationError(
        "Cross-schema relationships cannot use onDeleteCascade",
        "TemporaryEntry.owner",
        "PersistentOwner"
      )
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
              @Id val id: Long? = null
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
        "SqliteMagic_ImmutableGeneratedParent_Adapter.kt",
        "SqliteMagic_ImmutableGeneratedParent_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "child INTEGER DEFAULT NULL",
          "EntityRecursiveAdapter<ImmutableGeneratedParent",
          "operations.insert(",
          "adapter = SqliteMagic_ImmutableGeneratedChild_Adapter",
          "when (val result = operations.insert(",
          "when (val result = operations.persist(",
          "result.rowId?.let",
          "operations.rememberGeneratedId("
        )
      }
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "generatedRelationshipIds: Map<String, Long>",
          "generatedRelationshipIds[\"child\"] ?: entity.child.id",
          """Relationship \"child\" resolved to a NULL ID""",
          "OperationFailedException",
          "statement.bindLong"
        )
      }
  }

  @Test
  fun `marks WITHOUT ROWID models for shared runtime execution`() {
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
      .assertGeneratedSources("SqliteMagic_NaturalKey_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NaturalKey_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityDefaultIdentityAdapter<NaturalKey>",
          "override val withoutRowId: Boolean = true",
          "EntityIdentityStatementBinder<NaturalKey> by SqliteMagic_NaturalKey_Dao"
        )
        generatedSource.assertDoesNotContain(
          "override fun bindToInsertStatement(",
          "override fun bindToUpdateStatement(",
          "override fun bindNotNullForInsert(",
          "override fun bindNotNullForUpdate("
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
      .assertGeneratedSources("SqliteMagic_NullableIdentity_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NullableIdentity_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityDefaultIdentityAdapter<NullableIdentity>",
          "override fun hasIdentityValue(",
          "return entity.id != null",
          "override fun identity("
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
        "SqliteMagic_TeamMember_Adapter.kt",
        "TeamMemberTable.kt"
      )
      .withGeneratedSource("SqliteMagic_TeamMember_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "EntityRecursiveAdapter<TeamMember",
          "operations.insert(",
          "operations.update(",
          "operations.persist(",
          "adapter = SqliteMagic_Team_Adapter",
          "entity = entity.team"
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
          "internal fun addDeepQueryPartsInternal(",
          "val queryAliasContext = QueryAliasContext(",
          "rootTable = from.table,",
          "joins = from.joins",
          "queryAliasContext: QueryAliasContext",
          "val joinedTable0 = queryAliasContext.tableForAutomaticJoin(referencedTable0)",
          "TeamTable.TEAM",
          "queryDeep ->",
          "checkNotNull(",
          "SqliteMagic_TeamMember_Dao::fullObjectFromCursorPosition",
          "SqliteMagic_TeamMember_Dao::shallowObjectFromCursorPosition"
        )
        generatedSource.assertContainsInOrder(
          "JoinClause.indexOf(referencedTable0, joins, parentColumn0)",
          "if (joinIndex0 != -1)",
          "val userJoin = joins[joinIndex0]",
          "val joinedTable0 = queryAliasContext.tableForAutomaticJoin(referencedTable0)"
        )
        generatedSource.assertDoesNotContain("randomTableName()")
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
        "SqliteMagic_RecursiveTargetOwner_Dao.kt",
        "SqliteMagic_RequiredRecursiveTarget_Dao.kt"
      )
      .withGeneratedSource("RecursiveTargetOwnerTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "addShallowQueryParts",
          "internal fun addShallowQueryPartsInternal(",
          "fun addShallowQueryPartsInternal",
          "JoinClause.indexOf",
          "userJoin.tableNameInQuery()",
          "val queryAliasContext = QueryAliasContext(",
          "rootTable = from.table,",
          "joins = from.joins",
          "queryAliasContext: QueryAliasContext",
          "val joinedTable0 = queryAliasContext.tableForAutomaticJoin(referencedTable0)",
          "RequiredRecursiveTargetTable.REQUIRED_RECURSIVE_TARGET.addDeepQueryPartsInternal(",
          "queryAliasContext"
        )
        generatedSource.assertContainsInOrder(
          "JoinClause.indexOf(referencedTable0, joins, parentColumn0)",
          "if (joinIndex0 != -1)",
          "val userJoin = joins[joinIndex0]",
          "val joinedTable0 = queryAliasContext.tableForAutomaticJoin(referencedTable0)"
        )
        generatedSource.assertDoesNotContain("randomTableName()")
      }
      .withGeneratedSource("SqliteMagic_RecursiveTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "columnOffset.value += 3",
          "SqliteMagic_RequiredRecursiveTarget_Dao.shallowObjectFromCursorPosition(cursor, columnOffset)"
        )
      }
      .withGeneratedSource("SqliteMagic_RequiredRecursiveTarget_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "Required recursive relationship",
          "SqliteMagic_ShallowLeaf_Dao.newInstanceWithOnlyId"
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
        "RecursiveParentTable.kt",
        "SqliteMagic_RecursiveParent_Adapter.kt",
        "SqliteMagic_RecursiveChild_Adapter.kt"
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
  fun `allows recursive relationships with nullable IDs that cannot reconstruct the target`() {
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
      .isOk()
      .assertGeneratedSources("SqliteMagic_RecursiveNullableIdOwner_Dao.kt")
      .withGeneratedSource("SqliteMagic_RecursiveNullableIdOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          """Required recursive relationship \"target\" had a NULL ID""",
          "throw SQLException"
        )
        generatedSource.assertDoesNotContain("newInstanceWithOnlyId")
      }
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
        "SqliteMagic_OptionalChild_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_OptionalChild_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("parent TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_OptionalChild_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "parent?.let",
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
      .assertGeneratedSources(
        "SqliteMagic_LeafEntity_Adapter.kt",
        "LeafEntityTable.kt"
      )
      .withGeneratedSource("SqliteMagic_LeafEntity_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "branch TEXT DEFAULT '' REFERENCES branch_entity(id) ON DELETE CASCADE",
          "override val triggerTableNames"
        )
      }
      .withGeneratedSource("LeafEntityTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun addDeepQueryParts(",
          "internal fun addDeepQueryPartsInternal("
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

  @Test
  fun `keeps nullable ID storage metadata while requiring present relationship IDs`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableRelationshipIdModes.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class MutableGeneratedRelationshipTarget {
              @Id var id: Long? = null
              var value: String = ""
            }

            @Table
            data class ImmutableGeneratedRelationshipTarget(
              @Id val id: Long? = null,
              val value: String = ""
            )

            @Table
            data class NullableStringRelationshipTarget(
              @Id(autoIncrement = false) val id: String?,
              val value: String = ""
            )

            @Table
            data class NullableIntegerRelationshipTarget(
              @Id(autoIncrement = false) val id: Int?,
              val value: String = ""
            )

            @Table
            data class NullableRelationshipIdModes(
              @Id val id: String,
              val mutableTarget: MutableGeneratedRelationshipTarget,
              val immutableTarget: ImmutableGeneratedRelationshipTarget,
              @Column(handleRecursively = false)
              val stringTarget: NullableStringRelationshipTarget,
              @Column(handleRecursively = false)
              val integerTarget: NullableIntegerRelationshipTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableRelationshipIdModes_Adapter.kt",
        "SqliteMagic_NullableRelationshipIdModes_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableRelationshipIdModes_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "mutable_target INTEGER DEFAULT NULL",
          "immutable_target INTEGER DEFAULT NULL",
          "string_target TEXT DEFAULT NULL",
          "integer_target INTEGER DEFAULT NULL"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableRelationshipIdModes_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "generatedRelationshipIds[\"immutable_target\"] ?: entity.immutableTarget.id",
          """Relationship \"mutable_target\" resolved to a NULL ID""",
          """Relationship \"immutable_target\" resolved to a NULL ID""",
          """Relationship \"string_target\" resolved to a NULL ID""",
          """Relationship \"integer_target\" resolved to a NULL ID""",
          "OperationFailedException"
        )
      }
  }

  @Test
  fun `requires a non-null serialized value for transformed relationship IDs`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableSerializedRelationshipId.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class NullableSerializedRelationshipId(val value: String)

            @ObjectToDbValue
            fun nullableSerializedRelationshipIdToString(
              value: NullableSerializedRelationshipId
            ): String? = value.value.takeIf(String::isNotEmpty)

            @DbValueToObject
            fun stringToNullableSerializedRelationshipId(
              value: String?
            ): NullableSerializedRelationshipId = NullableSerializedRelationshipId(value.orEmpty())

            @Table
            data class NullableSerializedRelationshipTarget(
              @Id val id: NullableSerializedRelationshipId
            )

            @Table
            data class NullableSerializedRelationshipOwner(
              @Id val id: String,
              @Column(handleRecursively = false)
              val target: NullableSerializedRelationshipTarget
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableSerializedRelationshipOwner_Adapter.kt",
        "SqliteMagic_NullableSerializedRelationshipOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableSerializedRelationshipOwner_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("target TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_NullableSerializedRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "nullableSerializedRelationshipIdToString",
          """Relationship \"target\" resolved to a NULL ID""",
          "OperationFailedException"
        )
      }
  }

  @Test
  fun `uses nullable embedded paths for relationship schema and binding`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NullableEmbeddedRelationshipPath.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class EmbeddedRelationshipTarget(@Id val id: String)

            data class NullableEmbeddedRelationshipDetails(
              @Column(handleRecursively = false)
              val target: EmbeddedRelationshipTarget,
              val label: String
            )

            @Table
            data class NullableEmbeddedRelationshipOwner(
              @Id val id: String,
              @Embedded val details: NullableEmbeddedRelationshipDetails?
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableEmbeddedRelationshipOwner_Adapter.kt",
        "SqliteMagic_NullableEmbeddedRelationshipOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableEmbeddedRelationshipOwner_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("target TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_NullableEmbeddedRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "details?.target?.let",
          """Required relationship \"target\" had a NULL ID"""
        )
        generatedSource.assertDoesNotContain("OperationFailedException")
      }
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
