package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.InheritedMutableEntityTable
import com.siimkinks.sqlitemagic.MutableBodyEntityTable
import com.siimkinks.sqlitemagic.NonDataConstructorEntityTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.InheritedMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.MutableBodyEntity
import com.siimkinks.sqlitemagic.fixture.model.NonDataConstructorEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object ConstructionModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    NonDataConstructorEntityCase,
    MutableBodyEntityCase,
    InheritedMutableEntityCase
  )

  private object NonDataConstructorEntityCase : InsertModelCase<NonDataConstructorEntity> {
    override val name = "NonDataConstructorEntity"
    override val table = NonDataConstructorEntityTable.NON_DATA_CONSTRUCTOR_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NonDataConstructorEntity(value = "constructor")

    override fun insert(value: NonDataConstructorEntity) = value.insert()

    override fun expectedAfterInsert(value: NonDataConstructorEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object MutableBodyEntityCase : InsertModelCase<MutableBodyEntity> {
    override val name = "MutableBodyEntity"
    override val table = MutableBodyEntityTable.MUTABLE_BODY_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = MutableBodyEntity().apply {
      value = "mutable-body"
    }

    override fun insert(value: MutableBodyEntity) = value.insert()

    override fun expectedAfterInsert(value: MutableBodyEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object InheritedMutableEntityCase : InsertModelCase<InheritedMutableEntity> {
    override val name = "InheritedMutableEntity"
    override val table = InheritedMutableEntityTable.INHERITED_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = InheritedMutableEntity().apply {
      inheritedValue = "base"
      value = "child"
    }

    override fun insert(value: InheritedMutableEntity) = value.insert()

    override fun expectedAfterInsert(value: InheritedMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }
}
