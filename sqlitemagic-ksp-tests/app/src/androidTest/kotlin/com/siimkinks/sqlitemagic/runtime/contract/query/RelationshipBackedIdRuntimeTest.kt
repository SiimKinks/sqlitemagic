package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.LeafIdTable.Companion.LEAF_ID
import com.siimkinks.sqlitemagic.RelationshipIdTable.Companion.RELATIONSHIP_ID
import com.siimkinks.sqlitemagic.RelationshipOwnerTable.Companion.RELATIONSHIP_OWNER
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.fixture.model.LeafId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipOwner
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

class RelationshipBackedIdRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun insertStoresFinalStringValueInEveryRelationshipIdTable() {
    val seeded = seedOwner()

    assertThat(
      rawValueAndType(
        table = LEAF_ID,
        sql = "SELECT value, typeof(value) FROM leaf_id"
      )
    ).isEqualTo(listOf(seeded.leaf.value, "text"))
    assertThat(
      rawValueAndType(
        table = RELATIONSHIP_ID,
        sql = "SELECT id, typeof(id) FROM relationship_id"
      )
    ).isEqualTo(listOf(seeded.leaf.value, "text"))
    assertThat(
      rawValueAndType(
        table = RELATIONSHIP_OWNER,
        sql = "SELECT owner_id, relationship_id, typeof(relationship_id) FROM relationship_owner"
      )
    ).isEqualTo(listOf(seeded.owner.ownerId, seeded.leaf.value, "text"))
  }

  @Test
  fun typedPredicatesReconstructShallowRelationshipIdChain() {
    val seeded = seedOwner()

    assertThat(
      Select
        .from(RELATIONSHIP_OWNER)
        .where(RELATIONSHIP_OWNER.OWNER_ID IS seeded.owner.ownerId)
        .execute()
    ).containsExactly(seeded.owner)
    assertThat(
      Select
        .from(RELATIONSHIP_OWNER)
        .where(RELATIONSHIP_OWNER.RELATIONSHIP_ID IS seeded.leaf)
        .execute()
    ).containsExactly(seeded.owner)
  }

  @Test
  fun nullLeafValueFailsOwnerInsertWithFocusedRelationshipMessage() {
    val owner = RelationshipOwner(
      ownerId = "owner-null-leaf",
      relationshipId = RelationshipId(id = LeafId(value = null)),
      value = "owner-value"
    )

    val exception = assertThrows(OperationFailedException::class.java) {
      owner
        .insert()
        .execute()
    }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("Relationship \"relationship_id\" resolved to a NULL ID")
    assertThat(
      Select
        .from(RELATIONSHIP_OWNER)
        .execute()
    ).isEmpty()
  }

  private fun seedOwner(): SeededValues {
    val leaf = LeafId(value = "leaf-id")
    assertThat(
      leaf
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    val relationship = RelationshipId(id = leaf)
    assertThat(
      relationship
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    val owner = RelationshipOwner(
      ownerId = "owner-id",
      relationshipId = relationship,
      value = "owner-value"
    )
    assertThat(
      owner
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)
    return SeededValues(
      leaf = leaf,
      relationship = relationship,
      owner = owner
    )
  }

  private fun rawValueAndType(
    table: com.siimkinks.sqlitemagic.Table<*>,
    sql: String
  ): List<String?> = Select
    .raw(sql)
    .from(table)
    .execute()
    .use { cursor ->
      check(cursor.moveToFirst())
      List(size = cursor.columnCount) { index -> cursor.getString(index) }
    }

  private data class SeededValues(
    val leaf: LeafId,
    val relationship: RelationshipId,
    val owner: RelationshipOwner
  )
}
