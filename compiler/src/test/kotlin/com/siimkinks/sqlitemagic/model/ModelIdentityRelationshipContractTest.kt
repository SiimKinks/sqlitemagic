package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelIdentityRelationshipContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generates only safe operations for a table without an ID or non-null unique key`() {
    GENERATED
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
  fun `generates an eligible unique relationship column`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "UniqueRelationshipOwnerTable.kt",
        "SqliteMagic_UniqueRelationshipOwner_Adapter.kt"
      )
      .withGeneratedSource("UniqueRelationshipOwnerTable.kt") {
        it.assertContainsUnwrapped(
          "val TARGET: UniqueColumn<UniqueRelationshipOwner, String, Required, String, String, Required, String>",
          "parameterEncoding = SqlType.STRING."
        )
      }
      .withGeneratedSource("SqliteMagic_UniqueRelationshipOwner_Adapter.kt") {
        it.assertContains(
          "EntityIdentityAdapter<UniqueRelationshipOwner>",
          "override fun identity(",
          "override fun updateStatementSql(",
          "override fun identity(entity: UniqueRelationshipOwner, columnName: String): Any = entity.target.id"
        )
      }
  }

  @Test
  fun `does not treat a transformer with nullable storage output as an entity key`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableSerializedKey_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NullableSerializedKey_Adapter.kt") {
        it.assertContains(
          "internal object SqliteMagic_NullableSerializedKey_Adapter",
          "EntityAdapter<NullableSerializedKey>",
          "EntityStatementBinder<NullableSerializedKey> by SqliteMagic_NullableSerializedKey_Dao"
        )
      }
  }

  @Test
  fun `does not expose zero-argument terminals for no-ID identity operations`() {
    compiler
      .compile(
        sluggedNoteSource(),
        SourceFile.kotlin(
          name = "ZeroArgumentIdentityOperations.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.delete
            import com.siimkinks.sqlitemagic.persist
            import com.siimkinks.sqlitemagic.rxjava2.observe
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
  fun `rejects a hand-written unique column`() {
    compiler
      .compile(
        sluggedNoteSource(),
        SourceFile.kotlin(
          name = "HandWrittenUnique.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.Unique

            object NoteKey : Unique<SluggedNote>
          """
        )
      )
      .assertCompilationError("Extending sealed classes or interfaces from a different module is prohibited.")
  }

  @Test
  fun `requires and accepts an explicit unique column at no-ID identity operation terminals`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_SluggedNote_Dao.kt",
        "SluggedNoteTable.kt"
      )
      .withGeneratedSource("SqliteMagic_SluggedNote_Adapter.kt") {
        it.assertContains(
          "slug TEXT NOT NULL UNIQUE",
          "external_key TEXT NOT NULL UNIQUE",
          "EntityIdentityAdapter<SluggedNote>",
          "EntityIdentityStatementBinder<SluggedNote> by SqliteMagic_SluggedNote_Dao",
          "override fun identity(entity: SluggedNote, columnName: String): " +
              "Any = when (columnName) {",
          "override fun hasIdentityValue(entity: SluggedNote, columnName: String): Boolean = true",
          "override fun updateStatementSql(columnName: String): String = when (columnName) {",
          """"slug" -> entity.slug""",
          "else -> entity.externalKey"
        )
      }
      .withGeneratedSource("_SluggedNote.kt") {
        it.assertContains(
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
          "EntityBulkDeleteByColumnBuilder<SluggedNote>",
          "= UpdateByColumnBuilder(",
          "= PersistByColumnBuilder(",
          "= DeleteByColumnBuilder(",
          "= BulkUpdateByColumnBuilder(",
          "= BulkPersistByColumnBuilder(",
          "= BulkDeleteByColumnBuilder(",
          "entity = this"
        )
      }
  }

  @Test
  fun `resolves auto-increment mode from the raw annotation and ID storage`() {
    GENERATED
      .isOk()
      .withGeneratedSource("SqliteMagic_AutomaticLong_Adapter.kt") {
        it.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticString_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("TEXT NOT NULL PRIMARY KEY")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticTransformed_Adapter.kt") {
        it.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_DisabledLong_Adapter.kt") {
        it.assertDoesNotContain("AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_EnabledAutoIncrement_Adapter.kt") {
        it.assertContains("INTEGER PRIMARY KEY AUTOINCREMENT")
      }
      .withGeneratedSource("SqliteMagic_AutomaticRelationshipId_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("id INTEGER PRIMARY KEY")
        generatedSource.assertDoesNotContain("AUTOINCREMENT")
      }
  }

  @Test
  fun `rejects explicitly enabled auto-increment for an incompatible ID`() {
    compiler
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
  fun `rejects explicitly enabled auto-increment for a relationship-backed ID`() {
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_Account_Dao.kt",
        "SqliteMagic_Article_Dao.kt",
        "SqliteMagic_Article_Adapter.kt",
        "ArticleTable.kt"
      )
      .withGeneratedSource("SqliteMagic_Article_Adapter.kt") {
        it.assertContains(
          "author TEXT",
          "account TEXT",
          "publisher INTEGER"
        )
      }
      .withGeneratedSource("SqliteMagic_Article_Dao.kt") {
        it.assertContains(
          "author.id",
          "accountIdToString",
          "stringToAccountId",
          "publisher.id",
          "bindString",
          "bindLong"
        )
      }
      .withGeneratedSource("ArticleTable.kt") {
        it.assertContainsUnwrapped(
          "val AUTHOR: Column<Article, String, Required, String, String, Required, String>",
          "val ACCOUNT: Column<Article, AccountId, Required, AccountId, AccountId, Required, AccountId>",
          "parameterEncoding = SqlType.ACCOUNT_ID.",
          "val PUBLISHER: Column<Article, Long, Required, Long, Long, Required, Long>"
        )
      }
  }

  @Test
  fun `passes nullable transformed relationship storage values to the parser`() {
    GENERATED
      .isOk()
      .withGeneratedSource("OptionalIdOwnerTable.kt") {
        it.assertContainsUnwrapped(
          "val ACCOUNT: Column<OptionalIdOwner, OptionalAccountId, Required, OptionalAccountId, " +
              "OptionalAccountId, SqlNullable, OptionalAccountId?>",
          "parameterEncoding = SqlType.OPTIONAL_ACCOUNT_ID."
        )
      }
  }

  @Test
  fun `serializes and reconstructs relationship-backed IDs`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RelationshipOwner_Dao.kt",
        "RelationshipIdTable.kt",
        "RelationshipOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_RelationshipOwner_Dao.kt") {
        it.assertContains(
          "entity.relationshipId.id.`value`",
          "SqliteMagic_LeafId_Dao.newInstanceWithOnlyId",
          "SqliteMagic_RelationshipId_Dao.newInstanceWithOnlyId"
        )
      }
      .withGeneratedSource("RelationshipOwnerTable.kt") {
        it.assertContainsUnwrapped(
          "val RELATIONSHIP_ID: Column<RelationshipOwner, LeafId?, Required, LeafId, LeafId, SqlNullable, " +
              "LeafId?>",
          "value?.let { it.`value` }",
          "dbValue?.let { SqliteMagic_LeafId_Dao.newInstanceWithOnlyId(it) }"
        )
      }
      .withGeneratedSource("RelationshipIdTable.kt") {
        it.assertContainsUnwrapped(
          "val ID: UniqueColumn<RelationshipId, String, Required, String, String, Required, String>"
        )
      }
  }

  @Test
  fun `guards nullable final values in relationship-backed ID columns`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableRelationshipOwner_Dao.kt",
        "NullableRelationshipIdTable.kt",
        "NullableRelationshipOwnerTable.kt"
      )
      .withGeneratedSource("NullableRelationshipOwnerTable.kt") {
        it.assertContainsUnwrapped(
          "val RELATIONSHIP_ID: Column<NullableRelationshipOwner, NullableLeafId?, Required, NullableLeafId, " +
              "NullableLeafId, SqlNullable, NullableLeafId?>",
          "value?.let { it.`value` }",
          "dbValue?.let { SqliteMagic_NullableLeafId_Dao.newInstanceWithOnlyId(it) }"
        )
      }
      .withGeneratedSource("NullableRelationshipIdTable.kt") {
        it.assertContainsUnwrapped(
          "val ID: Column<NullableRelationshipId, String, Required, String, String, Required, String>"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "entity.relationshipId.id.`value`",
          """OperationFailedException("Relationship \"relationship_id\" resolved to a NULL ID")"""
        )
        generatedSource.assertDoesNotContain("entity.relationshipId.id?.`value`")
      }
  }

  @Test
  fun `propagates nullable relationship-backed IDs through the declared ID boundary`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_PropagatedRelationshipOwner_Dao.kt",
        "PropagatedRelationshipOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_PropagatedRelationshipOwner_Dao.kt") {
        it.assertContains(
          "SqliteMagic_PropagatedRelationshipId_Dao.newInstanceWithOnlyId(" +
              "SqliteMagic_NullableRelationshipLeaf_Dao.newInstanceWithOnlyId(cursor.getString(startIndex + 1)))"
        )
      }
      .withGeneratedSource("PropagatedRelationshipOwnerTable.kt") {
        it.assertContains(
          "dbValue?.let {",
          "SqliteMagic_NullableRelationshipLeaf_Dao.newInstanceWithOnlyId(it)"
        )
      }
  }

  @Test
  fun `does not pass nullable relationship storage into a non-null transformer input`() {
    GENERATED
      .isOk()
      .withGeneratedSource("RequiredAccountOwnerTable.kt") {
        it.assertContainsUnwrapped(
          "val ACCOUNT: Column<RequiredAccountOwner, RequiredAccountId, Required, RequiredAccountId, " +
              "RequiredAccountId, Required, RequiredAccountId>",
          "parameterEncoding = SqlType.REQUIRED_ACCOUNT_ID."
        )
      }
  }

  @Test
  fun `reconstructs an absent nullable relationship-backed ID as null`() {
    GENERATED
      .isOk()
      .withGeneratedSource("NullableSourceRelationshipOwnerTable.kt") {
        it.assertContains(
          "dbValue?.let { SqliteMagic_NullableSourceRelationshipLeaf_Dao.newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `distinguishes nullable referenced IDs from an absent relationship path`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NonNullTargetOwner_Adapter.kt",
        "SqliteMagic_NonNullTargetOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Adapter.kt") {
        it.assertContains("target TEXT NOT NULL)")
      }
      .withGeneratedSource("SqliteMagic_NonNullTargetOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          """Relationship \"target\" resolved to a NULL ID""",
          "OperationFailedException",
          "throw SQLException",
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
  fun `throws for an absent required recursive ID and reads a joined target when the read joined it`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_NonNullRecursiveTargetOwner_Dao.kt")
      .withGeneratedSource("SqliteMagic_NonNullRecursiveTargetOwner_Dao.kt") {
        it.assertContains(
          """Required recursive relationship \"target\" had a NULL ID""",
          "(related.read(cursor, 0) as NullableRecursiveIdTarget?) ?: " +
              "SqliteMagic_NullableRecursiveIdTarget_Dao.newInstanceWithOnlyId(cursor.getString(startIndex + 1))"
        )
      }
  }

  @Test
  fun `reads a nullable recursive relationship that needs a join as null when its ID is absent`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableRecursiveRelationshipOwner_Dao.kt",
        "NullableRecursiveRelationshipOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableRecursiveRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "cursor.isNull(startIndex + 1)",
          "related.read(cursor, 0) as NullableRecursiveRelationshipTarget"
        )
        generatedSource.assertDoesNotContain(
          "Required recursive relationship",
          "SqliteMagic_NullableRecursiveRelationshipTarget_Dao.newInstanceWithOnlyId("
        )
      }
      .withGeneratedSource("NullableRecursiveRelationshipOwnerTable.kt") {
        it.assertContains(
          "nullable = true",
          "read = RelationshipRead.ALWAYS",
          "SqliteMagic_NullableRecursiveRelationshipOwner_Dao.objectFromCursorPosition(" +
              "cursor, startIndex, checkNotNull(related))"
        )
      }
  }

  @Test
  fun `reads an embedded recursive relationship that needs a join inside its nullable graph`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableEmbeddedGraphOwner_Dao.kt",
        "NullableEmbeddedGraphOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableEmbeddedGraphOwner_Dao.kt") {
        it.assertContains(
          "graph = if",
          "related.read(cursor, 0) as EmbeddedRecursiveTarget"
        )
      }
      .withGeneratedSource("NullableEmbeddedGraphOwnerTable.kt") {
        it.assertContains(
          """columnName = "target"""",
          "read = RelationshipRead.ALWAYS"
        )
      }
  }

  @Test
  fun `does not emit foreign keys or transitive delete triggers for shallow relationships`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_ShallowChild_Adapter.kt")
      .withGeneratedSource("SqliteMagic_ShallowChild_Adapter.kt") { generatedSource ->
        generatedSource.assertContains(
          "parent TEXT NOT NULL)",
          "EntityDefaultIdentityAdapter<ShallowChild>",
          "EntityIdentityStatementBinder<ShallowChild> by SqliteMagic_ShallowChild_Dao",
          "override fun identity("
        )
        generatedSource.assertDoesNotContain(
          "REFERENCES shallow_parent(id)",
          "ON DELETE CASCADE",
          "triggerTableNames"
        )
      }
  }

  @Test
  fun `allows temporary relationships to persistent tables`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_TemporaryEntry_Adapter.kt")
  }

  @Test
  fun `rejects persistent relationships to temporary tables`() {
    compiler
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
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ImmutableGeneratedParent_Adapter.kt",
        "SqliteMagic_ImmutableGeneratedParent_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Adapter.kt") {
        it.assertContains(
          "child INTEGER NOT NULL)",
          "EntityRecursiveAdapter<ImmutableGeneratedParent",
          "adapter = SqliteMagic_ImmutableGeneratedChild_Adapter",
          "when (val result = operations.insert(",
          "when (val result = operations.persist(",
          "result.rowId?.let",
          "operations.rememberGeneratedId("
        )
      }
      .withGeneratedSource("SqliteMagic_ImmutableGeneratedParent_Dao.kt") {
        it.assertContains(
          "generatedRelationshipIds: Map<String, Long>",
          """generatedRelationshipIds["child"] ?: entity.child.id""",
          """Relationship \"child\" resolved to a NULL ID""",
          "OperationFailedException",
          "statement.bindLong"
        )
      }
  }

  @Test
  fun `marks WITHOUT ROWID models for shared runtime execution`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_NaturalKey_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NaturalKey_Adapter.kt") {
        it.assertContains(
          "EntityDefaultIdentityAdapter<NaturalKey>",
          "override val withoutRowId: Boolean = true",
          "EntityIdentityStatementBinder<NaturalKey> by SqliteMagic_NaturalKey_Dao"
        )
      }
      .withGeneratedSource("NaturalKeyTable.kt") {
        it.assertContainsUnwrapped(
          "val ID: UniqueColumn<NaturalKey, String, Required, String, String, Required, String>",
          "val OPTIONAL_VALUE: Column<NaturalKey, String, SqlNullable, String?, String?, SqlNullable, String?>"
        )
      }
  }

  @Test
  fun `persists a nullable missing ID through the insert path`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_NullableIdentity_Adapter.kt")
      .withGeneratedSource("SqliteMagic_NullableIdentity_Adapter.kt") {
        it.assertContains(
          "EntityDefaultIdentityAdapter<NullableIdentity>",
          "override fun hasIdentityValue(",
          "override fun hasIdentityValue(entity: NullableIdentity, columnName: String): Boolean = entity.id != null",
          "override fun identity("
        )
      }
  }

  @Test
  fun `generates ID-only reconstruction when other constructor parameters have defaults or are nullable`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_IdOnlyConstructible_Dao.kt",
        "SqliteMagic_IdOnlyInconstructible_Dao.kt",
        "SqliteMagic_IdOnlyNullable_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_IdOnlyConstructible_Dao.kt") {
        it.assertContains(
          "fun newInstanceWithOnlyId(id: String): IdOnlyConstructible",
          "IdOnlyConstructible(id = id)"
        )
      }
      .withGeneratedSource("SqliteMagic_IdOnlyInconstructible_Dao.kt") {
        it.assertDoesNotContain("newInstanceWithOnlyId")
      }
      .withGeneratedSource("SqliteMagic_IdOnlyNullable_Dao.kt") {
        it.assertContainsUnwrapped(
          "fun newInstanceWithOnlyId(id: String): IdOnlyNullable",
          "IdOnlyNullable(note = null, id = id, leaf = null)"
        )
      }
  }

  @Test
  fun `reads a recursive target that its ID builds with nulls from the ID unless the read joins it`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "IdOnlyNullableOwnerTable.kt",
        "SqliteMagic_IdOnlyNullableOwner_Dao.kt"
      )
      .withGeneratedSource("IdOnlyNullableOwnerTable.kt") {
        it.assertContains("read = RelationshipRead.DEEP")
      }
      .withGeneratedSource("SqliteMagic_IdOnlyNullableOwner_Dao.kt") {
        it.assertContainsUnwrapped(
          "(related.read(cursor, 0) as IdOnlyNullable?) ?: " +
              "SqliteMagic_IdOnlyNullable_Dao.newInstanceWithOnlyId(cursor.getString(startIndex + 1))"
        )
      }
  }

  @Test
  fun `allows shallow relationships to targets that their ID builds with nulls`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "IdOnlyNullableReferenceTable.kt",
        "SqliteMagic_IdOnlyNullableReference_Dao.kt"
      )
      .withGeneratedSource("IdOnlyNullableReferenceTable.kt") {
        it.assertContains("read = RelationshipRead.ID_ONLY")
      }
      .withGeneratedSource("SqliteMagic_IdOnlyNullableReference_Dao.kt") {
        it.assertContains("SqliteMagic_IdOnlyNullable_Dao.newInstanceWithOnlyId(")
      }
  }

  @Test
  fun `generates recursive relationship persistence and joined or ID-only retrieval`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TeamMember_Dao.kt",
        "SqliteMagic_TeamMember_Adapter.kt",
        "TeamMemberTable.kt"
      )
      .withGeneratedSource("SqliteMagic_TeamMember_Adapter.kt") {
        it.assertContains(
          "EntityRecursiveAdapter<TeamMember",
          "operations.insert(",
          "operations.update(",
          "operations.persist(",
          "adapter = SqliteMagic_Team_Adapter",
          "entity = entity.team"
        )
      }
      .withGeneratedSource("SqliteMagic_TeamMember_Dao.kt") {
        it.assertContains(
          "fun objectFromCursorPosition(",
          "related: RelatedEntities,",
          "(related.read(cursor, 0) as Team?) ?: " +
              "SqliteMagic_Team_Dao.newInstanceWithOnlyId(cursor.getString(startIndex + 1))"
        )
      }
      .withGeneratedSource("TeamMemberTable.kt") {
        it
          .assertContainsUnwrapped(
            "val TEAM: Column<TeamMember, String, Required, String, String, Required, String>"
          )
          .assertContains(
            """columnName = "team"""",
            "target = { TeamTable.TEAM }",
            "read = RelationshipRead.DEEP",
            "SqliteMagic_TeamMember_Dao.objectFromCursorPosition(cursor, startIndex, checkNotNull(related))"
          )
      }
  }

  @Test
  fun `always joins a required recursive target that its ID cannot build`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "RecursiveTargetOwnerTable.kt",
        "SqliteMagic_RecursiveTargetOwner_Dao.kt",
        "SqliteMagic_RequiredRecursiveTarget_Dao.kt"
      )
      .withGeneratedSource("RecursiveTargetOwnerTable.kt") {
        it.assertContains(
          "read = RelationshipRead.ALWAYS",
          "SqliteMagic_RecursiveTargetOwner_Dao.objectFromCursorPosition(cursor, startIndex, checkNotNull(related))"
        )
      }
      .withGeneratedSource("SqliteMagic_RecursiveTargetOwner_Dao.kt") {
        it.assertContains("related.read(cursor, 0) as RequiredRecursiveTarget")
      }
      .withGeneratedSource("SqliteMagic_RequiredRecursiveTarget_Dao.kt") {
        it.assertContains(
          "Required recursive relationship",
          "(related.read(cursor, 0) as ShallowLeaf?) ?: ",
          "SqliteMagic_ShallowLeaf_Dao.newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `allows a relationship cycle when a non-recursive edge breaks the persistence graph`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "RecursiveParentTable.kt",
        "SqliteMagic_RecursiveParent_Adapter.kt",
        "SqliteMagic_RecursiveChild_Adapter.kt"
      )
  }

  @Test
  fun `rejects shallow relationships that cannot reconstruct the target from only its ID`() {
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_RecursiveNullableIdOwner_Dao.kt",
        "RecursiveNullableIdOwnerTable.kt"
      )
      .withGeneratedSource("SqliteMagic_RecursiveNullableIdOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          """Required recursive relationship \"target\" had a NULL ID""",
          "throw SQLException",
          "related.read(cursor, 0) as InconstructibleNullableIdTarget"
        )
        generatedSource.assertDoesNotContain("newInstanceWithOnlyId(")
      }
      .withGeneratedSource("RecursiveNullableIdOwnerTable.kt") {
        it.assertContains("read = RelationshipRead.ALWAYS")
      }
  }

  @Test
  fun `rejects relationship-backed IDs whose target cannot be reconstructed from its ID`() {
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_OptionalChild_Dao.kt",
        "SqliteMagic_OptionalChild_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_OptionalChild_Adapter.kt") {
        it.assertContains("parent TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_OptionalChild_Dao.kt") {
        it.assertContains(
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LeafEntity_Adapter.kt",
        "LeafEntityTable.kt"
      )
      .withGeneratedSource("SqliteMagic_LeafEntity_Adapter.kt") {
        it.assertContains(
          "branch TEXT NOT NULL REFERENCES branch_entity(id) ON DELETE CASCADE",
          "override val triggerTableNames"
        )
      }
  }

  @Test
  fun `rejects relationships to tables without an explicit ID`() {
    compiler
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
    compiler
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableRelationshipIdModes_Adapter.kt",
        "SqliteMagic_NullableRelationshipIdModes_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableRelationshipIdModes_Adapter.kt") {
        it.assertContains(
          ", mutable_target INTEGER NOT NULL",
          ", immutable_target INTEGER NOT NULL",
          ", string_target TEXT NOT NULL",
          ", integer_target INTEGER NOT NULL"
        )
      }
      .withGeneratedSource("SqliteMagic_NullableRelationshipIdModes_Dao.kt") {
        it.assertContains(
          """generatedRelationshipIds["immutable_target"] ?: entity.immutableTarget.id""",
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
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableSerializedRelationshipOwner_Adapter.kt",
        "SqliteMagic_NullableSerializedRelationshipOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableSerializedRelationshipOwner_Adapter.kt") {
        it.assertContains("target TEXT NOT NULL)")
      }
      .withGeneratedSource("SqliteMagic_NullableSerializedRelationshipOwner_Dao.kt") {
        it.assertContains(
          "nullableSerializedRelationshipIdToString",
          """Relationship \"target\" resolved to a NULL ID""",
          "OperationFailedException"
        )
      }
  }

  @Test
  fun `uses nullable embedded paths for relationship schema and binding`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_NullableEmbeddedRelationshipOwner_Adapter.kt",
        "SqliteMagic_NullableEmbeddedRelationshipOwner_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_NullableEmbeddedRelationshipOwner_Adapter.kt") {
        it.assertContains("target TEXT DEFAULT NULL")
      }
      .withGeneratedSource("SqliteMagic_NullableEmbeddedRelationshipOwner_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "details?.target?.let",
          """Required relationship \"target\" had a NULL ID"""
        )
        generatedSource.assertDoesNotContain("OperationFailedException")
      }
  }

  companion object {
    private val GENERATED by lazy {
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
          ),
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
          ),
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
          ),
          sluggedNoteSource(),
          SourceFile.kotlin(
            name = "ExplicitIdentityOperations.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SluggedNoteTable
              import com.siimkinks.sqlitemagic.delete
              import com.siimkinks.sqlitemagic.persist
              import com.siimkinks.sqlitemagic.rxjava2.observe
              import com.siimkinks.sqlitemagic.update

              private val slugColumn = SluggedNoteTable.SLUGGED_NOTE.SLUG
              private val aliasedSlugColumn = SluggedNoteTable.SLUGGED_NOTE
                .AS("note_alias")
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
          ),
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
          ),
          SourceFile.kotlin(
            name = "ExplicitModels.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              data class DisabledLong(
                @Id(autoIncrement = false) val id: Long
              )

              @Table
              data class EnabledAutoIncrement(
                @Id(autoIncrement = true) val id: Long
              )
            """
          ),
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
          ),
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
          ),
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
          ),
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
          ),
          SourceFile.kotlin(
            name = "NullableRelationshipBackedId.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              data class NullableLeafId(
                @Id val value: String?
              )

              @Table
              data class NullableRelationshipId(
                @Id
                @Column(handleRecursively = false)
                val id: NullableLeafId
              )

              @Table
              data class NullableRelationshipOwner(
                @Id val ownerId: String,
                @Column(handleRecursively = false)
                val relationshipId: NullableRelationshipId
              )
            """
          ),
          SourceFile.kotlin(
            name = "PropagatedRelationshipBackedId.kt",
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
              data class PropagatedRelationshipId(
                @Id
                @Column(handleRecursively = false)
                val id: NullableRelationshipLeaf?
              )

              @Table
              data class PropagatedRelationshipOwner(
                @Id val ownerId: String,
                @Column(handleRecursively = false)
                val target: PropagatedRelationshipId
              )
            """
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
          SourceFile.kotlin(
            name = "IdOnlyReconstruction.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
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

              @Table
              data class IdOnlyNullable(
                val note: String?,
                @Id val id: String,
                val label: String = "",
                val fallback: String? = "fallback",
                val leaf: ShallowLeaf?
              )

              @Table
              data class IdOnlyNullableOwner(
                @Id val id: String,
                @Column(handleRecursively = true) val target: IdOnlyNullable
              )

              @Table
              data class IdOnlyNullableReference(
                @Id val id: String,
                @Column(handleRecursively = false) val target: IdOnlyNullable
              )
            """
          ),
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
          ),
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
          ),
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
          ),
          SourceFile.kotlin(
            name = "InconstructibleNullableIdRelationship.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              data class InconstructibleNullableIdTarget(
                @Id val id: String?,
                val requiredValue: String
              )

              @Table
              data class RecursiveNullableIdOwner(
                @Id val id: String,
                @Column(handleRecursively = true) val target: InconstructibleNullableIdTarget
              )
            """
          ),
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
          ),
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
          ),
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
          ),
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
          ),
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
          ),
          processingStepsFactory = ::modelProcessingSteps
        )
        .generatedSources()
    }
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
