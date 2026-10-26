package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.RelationshipOwnerTable.Companion.RELATIONSHIP_OWNER
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.fixture.model.LeafId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipOwner
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.readRows
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

internal class RelationshipBackedIdRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun insertStoresFinalStringValueInEveryRelationshipIdTable() {
    val owner = seedOwner()
    val leafValue = owner.relationshipId.id.value

    assertThat(
      listOf(
        "SELECT value, typeof(value) FROM leaf_id",
        "SELECT id, typeof(id) FROM relationship_id",
        "SELECT owner_id, relationship_id, typeof(relationship_id) FROM relationship_owner"
      ).map {
        SELECT
          .RAW(it)
          .toCursor()
          .execute()
          .use(Cursor::readRows)
          .single()
      }
    ).isEqualTo(
      listOf(
        listOf(leafValue, "text"),
        listOf(leafValue, "text"),
        listOf(owner.ownerId, leafValue, "text")
      )
    )
  }

  @Test
  fun typedPredicatesReconstructShallowRelationshipIdChain() {
    val owner = seedOwner()

    assertThat(
      SELECT
        .FROM(RELATIONSHIP_OWNER)
        .WHERE(RELATIONSHIP_OWNER.OWNER_ID IS owner.ownerId)
        .execute()
    ).containsExactly(owner)
    assertThat(
      SELECT
        .FROM(RELATIONSHIP_OWNER)
        .WHERE(RELATIONSHIP_OWNER.RELATIONSHIP_ID IS owner.relationshipId.id)
        .execute()
    ).containsExactly(owner)
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
      .isEqualTo("""Relationship "relationship_id" resolved to a NULL ID""")
    assertThat(
      SELECT
        .FROM(RELATIONSHIP_OWNER)
        .execute()
    ).isEmpty()
  }

  private fun seedOwner(): RelationshipOwner {
    val leaf = LeafId(value = "leaf-id")
    insertedResult(
      result = leaf
        .insert()
        .execute(),
      modelName = "LeafId"
    )

    val relationship = RelationshipId(id = leaf)
    insertedResult(
      result = relationship
        .insert()
        .execute(),
      modelName = "RelationshipId"
    )

    val owner = RelationshipOwner(
      ownerId = "owner-id",
      relationshipId = relationship,
      value = "owner-value"
    )
    insertedResult(
      result = owner
        .insert()
        .execute(),
      modelName = "RelationshipOwner"
    )
    return owner
  }
}
