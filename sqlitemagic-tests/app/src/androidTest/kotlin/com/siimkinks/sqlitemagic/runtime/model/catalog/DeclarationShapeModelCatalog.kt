package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.JavaMutableEntityTable.Companion.JAVA_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.NestedModelContainer_NestedEntityTable.Companion.NESTED_MODEL_CONTAINER__NESTED_ENTITY
import com.siimkinks.sqlitemagic.ValueClassEntityTable.Companion.VALUE_CLASS_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.JavaMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.NestedModelContainer.NestedEntity
import com.siimkinks.sqlitemagic.fixture.model.ValueClassEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardPersistModelCase
import com.siimkinks.sqlitemagic.update

internal object DeclarationShapeModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    NestedEntityCase,
    ValueClassEntityCase,
    JavaMutableEntityCase
  )

  private object NestedEntityCase :
    StandardPersistModelCase<NestedEntity>,
    StandardDeleteModelCase<NestedEntity> {
    override val name = "NestedEntity"
    override val table = NESTED_MODEL_CONTAINER__NESTED_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NestedEntity(
      id = "nested-entity-id-$sequence",
      value = "nested-entity-value-$sequence"
    )

    override fun insert(value: NestedEntity) = value.insert()

    override fun update(value: NestedEntity) = value.update()

    override fun persist(value: NestedEntity) = value.persist()

    override fun delete(value: NestedEntity) = value.delete()

    override fun expectedAfterInsert(value: NestedEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: NestedEntity, sequence: Int) = value.copy(
      value = "nested-entity-updated-value-$sequence"
    )

    override fun toString() = name
  }

  private object ValueClassEntityCase :
    StandardPersistModelCase<ValueClassEntity>,
    StandardDeleteModelCase<ValueClassEntity> {
    override val name = "ValueClassEntity"
    override val table = VALUE_CLASS_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ValueClassEntity(value = "value-class-value-$sequence")

    override fun insert(value: ValueClassEntity) = value.insert()

    override fun update(value: ValueClassEntity) = value.update()

    override fun persist(value: ValueClassEntity) = value.persist()

    override fun delete(value: ValueClassEntity) = value.delete()

    override fun expectedAfterInsert(value: ValueClassEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: ValueClassEntity, sequence: Int) = value

    override fun toString() = name
  }

  private object JavaMutableEntityCase :
    StandardPersistModelCase<JavaMutableEntity>,
    StandardDeleteModelCase<JavaMutableEntity> {
    override val name = "JavaMutableEntity"
    override val table = JAVA_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = JavaMutableEntity().apply {
      id = "java-mutable-id-$sequence"
      value = "java-mutable-value-$sequence"
    }

    override fun insert(value: JavaMutableEntity) = value.insert()

    override fun update(value: JavaMutableEntity) = value.update()

    override fun persist(value: JavaMutableEntity) = value.persist()

    override fun delete(value: JavaMutableEntity) = value.delete()

    override fun expectedAfterInsert(value: JavaMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: JavaMutableEntity, sequence: Int) = value.apply {
      this.value = "java-mutable-updated-value-$sequence"
    }

    override fun toString() = name
  }
}
