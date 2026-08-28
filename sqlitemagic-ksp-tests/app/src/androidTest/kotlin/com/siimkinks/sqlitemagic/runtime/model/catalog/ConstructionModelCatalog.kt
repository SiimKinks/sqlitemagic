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
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.PersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
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
    BulkInsertModelCase<MutableBodyEntity>,
    BulkUpdateModelCase<MutableBodyEntity>,
    PersistModelCase<MutableBodyEntity> {
    override val name = "MutableBodyEntity"
    override val table = MUTABLE_BODY_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = MutableBodyEntity().apply {
      value = "mutable-body-$sequence"
    }

    override fun insert(value: MutableBodyEntity) = value.insert()

    override fun bulkInsert(values: List<MutableBodyEntity>) = MutableBodyEntitys.insert(values)

    override fun expectedAfterInsert(value: MutableBodyEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: MutableBodyEntity, sequence: Int) = value.apply {
      this.value = "mutable-body-updated-$sequence"
    }

    override fun executeUpdate(value: MutableBodyEntity) = value
      .update()
      .execute()

    override fun observeUpdate(value: MutableBodyEntity) = value
      .update()
      .observe()

    override fun executeBulkUpdate(values: List<MutableBodyEntity>) = MutableBodyEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<MutableBodyEntity>) = MutableBodyEntitys
      .update(values)
      .observe()

    override fun executePersist(value: MutableBodyEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: MutableBodyEntity) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object InheritedMutableEntityCase :
    BulkInsertModelCase<InheritedMutableEntity>,
    BulkUpdateModelCase<InheritedMutableEntity>,
    PersistModelCase<InheritedMutableEntity> {
    override val name = "InheritedMutableEntity"
    override val table = INHERITED_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = InheritedMutableEntity().apply {
      inheritedValue = "base-$sequence"
      value = "child-$sequence"
    }

    override fun insert(value: InheritedMutableEntity) = value.insert()

    override fun bulkInsert(values: List<InheritedMutableEntity>) = InheritedMutableEntitys.insert(values)

    override fun expectedAfterInsert(value: InheritedMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: InheritedMutableEntity, sequence: Int) = value.apply {
      inheritedValue = "base-updated-$sequence"
      this.value = "child-updated-$sequence"
    }

    override fun executeUpdate(value: InheritedMutableEntity) = value
      .update()
      .execute()

    override fun observeUpdate(value: InheritedMutableEntity) = value
      .update()
      .observe()

    override fun executeBulkUpdate(values: List<InheritedMutableEntity>) = InheritedMutableEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<InheritedMutableEntity>) = InheritedMutableEntitys
      .update(values)
      .observe()

    override fun executePersist(value: InheritedMutableEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: InheritedMutableEntity) = value
      .persist()
      .observe()

    override fun toString() = name
  }
}
