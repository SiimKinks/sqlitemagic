package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.RelationshipOwnerTable.Companion.RELATIONSHIP_OWNER
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.LeafId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipId
import com.siimkinks.sqlitemagic.fixture.model.RelationshipOwner
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardPersistModelCase
import com.siimkinks.sqlitemagic.update

internal object RelationshipBackedIdModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(RelationshipOwnerCase)

  private object RelationshipOwnerCase :
    StandardPersistModelCase<RelationshipOwner>,
    StandardDeleteModelCase<RelationshipOwner> {
    override val name = "RelationshipOwner"
    override val table = RELATIONSHIP_OWNER
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = RelationshipOwner(
      ownerId = "relationship-owner-$sequence",
      relationshipId = RelationshipId(
        id = LeafId(
          value = "relationship-leaf-$sequence"
        )
      ),
      value = "relationship-owner-value-$sequence"
    )

    override fun insert(value: RelationshipOwner) = value.insert()

    override fun update(value: RelationshipOwner) = value.update()

    override fun persist(value: RelationshipOwner) = value.persist()

    override fun delete(value: RelationshipOwner) = value.delete()

    override fun expectedAfterInsert(value: RelationshipOwner, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: RelationshipOwner, sequence: Int) = value.copy(
      value = "relationship-owner-updated-value-$sequence"
    )

    override fun toString() = name
  }
}
