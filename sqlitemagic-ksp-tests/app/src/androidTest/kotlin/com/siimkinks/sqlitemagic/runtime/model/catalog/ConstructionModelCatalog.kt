package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.InheritedMutableEntityTable.Companion.INHERITED_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.InheritedMutableEntitys
import com.siimkinks.sqlitemagic.MutableBodyEntityTable.Companion.MUTABLE_BODY_ENTITY
import com.siimkinks.sqlitemagic.MutableBodyEntitys
import com.siimkinks.sqlitemagic.NonDataConstructorEntityTable.Companion.NON_DATA_CONSTRUCTOR_ENTITY
import com.siimkinks.sqlitemagic.NonDataConstructorEntitys
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.InheritedMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.MutableBodyEntity
import com.siimkinks.sqlitemagic.fixture.model.NonDataConstructorEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.update

internal object ConstructionModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    NonDataConstructorEntityCase,
    MutableBodyEntityCase,
    InheritedMutableEntityCase,
  )

  private object NonDataConstructorEntityCase : BulkInsertModelCase<NonDataConstructorEntity> {
    override val name = "NonDataConstructorEntity"
    override val table = NON_DATA_CONSTRUCTOR_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NonDataConstructorEntity(value = "constructor-$sequence")

    override fun insert(value: NonDataConstructorEntity) = value.insert()

    override fun bulkInsert(values: List<NonDataConstructorEntity>) = NonDataConstructorEntitys.insert(values)

    override fun expectedAfterInsert(value: NonDataConstructorEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object MutableBodyEntityCase :
    StandardBulkPersistModelCase<MutableBodyEntity> {
    override val name = "MutableBodyEntity"
    override val table = MUTABLE_BODY_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = MutableBodyEntity().apply {
      value = "mutable-body-$sequence"
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = MutableBodyEntity::insert,
      bulkInsert = MutableBodyEntitys::insert,
      update = MutableBodyEntity::update,
      bulkUpdate = MutableBodyEntitys::update,
      persist = MutableBodyEntity::persist,
      bulkPersist = MutableBodyEntitys::persist
    )

    override fun expectedAfterInsert(value: MutableBodyEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: MutableBodyEntity, sequence: Int) = value.apply {
      this.value = "mutable-body-updated-$sequence"
    }

    override fun toString() = name
  }

  private object InheritedMutableEntityCase :
    StandardBulkPersistModelCase<InheritedMutableEntity> {
    override val name = "InheritedMutableEntity"
    override val table = INHERITED_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = InheritedMutableEntity().apply {
      inheritedValue = "base-$sequence"
      value = "child-$sequence"
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = InheritedMutableEntity::insert,
      bulkInsert = InheritedMutableEntitys::insert,
      update = InheritedMutableEntity::update,
      bulkUpdate = InheritedMutableEntitys::update,
      persist = InheritedMutableEntity::persist,
      bulkPersist = InheritedMutableEntitys::persist
    )

    override fun expectedAfterInsert(value: InheritedMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: InheritedMutableEntity, sequence: Int) = value.apply {
      inheritedValue = "base-updated-$sequence"
      this.value = "child-updated-$sequence"
    }

    override fun toString() = name
  }
}
