package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.BlobEntityTable
import com.siimkinks.sqlitemagic.CustomColumnEntityTable
import com.siimkinks.sqlitemagic.EntityWithIgnoredValueTable
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable
import com.siimkinks.sqlitemagic.SelectiveColumnsEntityTable
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.BlobEntity
import com.siimkinks.sqlitemagic.fixture.model.CustomColumnEntity
import com.siimkinks.sqlitemagic.fixture.model.EntityWithIgnoredValue
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.fixture.model.SelectiveColumnsEntity
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object ScalarModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    SimpleMutableEntityCase,
    SimpleMutableEntityWithPresetAutoIdCase,
    ImmutableValueWithNullableFieldsCase,
    ImmutableValueWithFieldsCase,
    CustomColumnEntityCase,
    SelectiveColumnsEntityCase,
    EntityWithIgnoredValueCase,
    BlobEntityCase,
  )

  private object SimpleMutableEntityCase : InsertModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntity"
    override val table = SimpleMutableEntityTable.SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = null,
      value = "simple-mutable-entity-$sequence",
      boxedBoolean = null,
      primitiveBoolean = true
    )

    override fun insert(value: SimpleMutableEntity) = value.insert()

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SimpleMutableEntityWithPresetAutoIdCase : InsertModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntityWithPresetAutoId"
    override val table = SimpleMutableEntityTable.SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = 9001L,
      value = "preset-simple-mutable-entity",
      boxedBoolean = true,
      primitiveBoolean = false
    )

    override fun insert(value: SimpleMutableEntity) = value.insert()

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun verifyAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) {
      check(value.id != 9001L) { "Preset SimpleMutableEntity ID must be replaced by the generated row ID" }
      check(value.id == result.rowId) { "Preset SimpleMutableEntity ID must equal the inserted row ID" }
    }

    override fun toString() = name
  }

  private object ImmutableValueWithNullableFieldsCase : InsertModelCase<ImmutableValueWithNullableFields> {
    override val name = "ImmutableValueWithNullableFields"
    override val table = ImmutableValueWithNullableFieldsTable.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ImmutableValueWithNullableFields(
      id = null,
      string = "immutable-value-$sequence",
      aBoolean = null,
      integer = sequence
    )

    override fun insert(value: ImmutableValueWithNullableFields) = value.insert()

    override fun expectedAfterInsert(
      value: ImmutableValueWithNullableFields,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun toString() = name
  }

  private object ImmutableValueWithFieldsCase : InsertModelCase<ImmutableValueWithFields> {
    override val name = "ImmutableValueWithFields"
    override val table = ImmutableValueWithFieldsTable.IMMUTABLE_VALUE_WITH_FIELDS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ImmutableValueWithFields(
      id = null,
      stringValue = "transformed-value",
      aBoolean = true,
      integer = 17,
      aDouble = 2.5,
      aShort = 3,
      transformableObject = TransformableObject(value = 23)
    )

    override fun insert(value: ImmutableValueWithFields) = value.insert()

    override fun expectedAfterInsert(
      value: ImmutableValueWithFields,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun toString() = name
  }

  private object CustomColumnEntityCase : InsertModelCase<CustomColumnEntity> {
    override val name = "CustomColumnEntity"
    override val table = CustomColumnEntityTable.CUSTOM_COLUMN_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = CustomColumnEntity(
      id = null,
      value = "custom-column"
    )

    override fun insert(value: CustomColumnEntity) = value.insert()

    override fun expectedAfterInsert(value: CustomColumnEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SelectiveColumnsEntityCase : InsertModelCase<SelectiveColumnsEntity> {
    override val name = "SelectiveColumnsEntity"
    override val table = SelectiveColumnsEntityTable.SELECTIVE_COLUMNS_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SelectiveColumnsEntity().apply {
      persistedValue = "selective-persisted-$sequence"
      transientValue = "selective-transient-$sequence"
    }

    override fun insert(value: SelectiveColumnsEntity) = value.insert()

    override fun expectedAfterInsert(
      value: SelectiveColumnsEntity,
      result: EntityInsertResult.Inserted
    ) = SelectiveColumnsEntity().apply {
      id = checkNotNull(result.rowId)
      persistedValue = value.persistedValue
    }

    override fun toString() = name
  }

  private object EntityWithIgnoredValueCase : InsertModelCase<EntityWithIgnoredValue> {
    override val name = "EntityWithIgnoredValue"
    override val table = EntityWithIgnoredValueTable.ENTITY_WITH_IGNORED_VALUE
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithIgnoredValue().apply {
      persistedValue = "ignored-persisted-$sequence"
      ignoredValue = "ignored-value-$sequence"
    }

    override fun insert(value: EntityWithIgnoredValue) = value.insert()

    override fun expectedAfterInsert(
      value: EntityWithIgnoredValue,
      result: EntityInsertResult.Inserted
    ) = EntityWithIgnoredValue().apply {
      id = checkNotNull(result.rowId)
      persistedValue = value.persistedValue
    }

    override fun toString() = name
  }

  private object BlobEntityCase : InsertModelCase<BlobEntity> {
    override val name = "BlobEntity"
    override val table = BlobEntityTable.BLOB_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = BlobEntity(
      id = null,
      payload = byteArrayOf(
        sequence.toByte(),
        (sequence + 1).toByte(),
        (sequence + 2).toByte()
      )
    )

    override fun insert(value: BlobEntity) = value.insert()

    override fun expectedAfterInsert(
      value: BlobEntity,
      result: EntityInsertResult.Inserted
    ) = BlobEntity(
      id = checkNotNull(result.rowId),
      payload = value.payload
    )

    override fun toString() = name
  }
}
