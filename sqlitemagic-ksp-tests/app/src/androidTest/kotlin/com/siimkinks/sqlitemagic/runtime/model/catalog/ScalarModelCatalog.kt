package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.BlobEntityTable.Companion.BLOB_ENTITY
import com.siimkinks.sqlitemagic.BlobEntitys
import com.siimkinks.sqlitemagic.CustomColumnEntityTable.Companion.CUSTOM_COLUMN_ENTITY
import com.siimkinks.sqlitemagic.CustomColumnEntitys
import com.siimkinks.sqlitemagic.EntityWithIgnoredValueTable.Companion.ENTITY_WITH_IGNORED_VALUE
import com.siimkinks.sqlitemagic.EntityWithIgnoredValues
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldss
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldss
import com.siimkinks.sqlitemagic.SelectiveColumnsEntityTable.Companion.SELECTIVE_COLUMNS_ENTITY
import com.siimkinks.sqlitemagic.SelectiveColumnsEntitys
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SimpleMutableEntitys
import com.siimkinks.sqlitemagic.delete
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
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingAllNullPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.update

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

  internal val representativeEmptyBulkCase: BulkPersistModelCase<SimpleMutableEntity> = SimpleMutableEntityCase

  private object SimpleMutableEntityCase :
    TriggerModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntity"
    override val table = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = null,
      value = "simple-mutable-entity-$sequence",
      boxedBoolean = null,
      primitiveBoolean = true
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = SimpleMutableEntity::insert,
      bulkInsert = SimpleMutableEntitys::insert,
      update = SimpleMutableEntity::update,
      bulkUpdate = SimpleMutableEntitys::update,
      persist = SimpleMutableEntity::persist,
      bulkPersist = SimpleMutableEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = SimpleMutableEntity::delete,
      bulkDelete = SimpleMutableEntitys::delete,
      deleteTable = SimpleMutableEntitys::deleteTable
    )

    override fun updatedValue(value: SimpleMutableEntity, sequence: Int) = value.copy(
      value = "simple-mutable-entity-updated-$sequence",
      boxedBoolean = sequence % 2 == 0,
      primitiveBoolean = sequence % 2 != 0
    )

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SimpleMutableEntityWithPresetAutoIdCase :
    StandardBulkPersistModelCase<SimpleMutableEntity>,
    StandardBulkDeleteModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntityWithPresetAutoId"
    override val table = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = 9001L,
      value = "preset-simple-mutable-entity-$sequence",
      boxedBoolean = true,
      primitiveBoolean = false
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = SimpleMutableEntity::insert,
      bulkInsert = SimpleMutableEntitys::insert,
      update = SimpleMutableEntity::update,
      bulkUpdate = SimpleMutableEntitys::update,
      persist = SimpleMutableEntity::persist,
      bulkPersist = SimpleMutableEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = SimpleMutableEntity::delete,
      bulkDelete = SimpleMutableEntitys::delete,
      deleteTable = SimpleMutableEntitys::deleteTable
    )

    override fun updatedValue(value: SimpleMutableEntity, sequence: Int) = value.copy(
      value = "preset-simple-mutable-entity-updated-$sequence",
      boxedBoolean = sequence % 2 != 0,
      primitiveBoolean = sequence % 2 == 0
    )

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun verifyAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) {
      check(value.id != 9001L) { "Preset SimpleMutableEntity ID must be replaced by the generated row ID" }
      check(value.id == result.rowId) { "Preset SimpleMutableEntity ID must equal the inserted row ID" }
    }

    override fun toString() = name
  }

  private object ImmutableValueWithNullableFieldsCase :
    StandardNullOmittingPersistModelCase<ImmutableValueWithNullableFields>,
    StandardBulkDeleteModelCase<ImmutableValueWithNullableFields>,
    NullOmittingAllNullPersistModelCase<ImmutableValueWithNullableFields> {
    override val name = "ImmutableValueWithNullableFields"
    override val table = IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ImmutableValueWithNullableFields(
      id = null,
      string = "immutable-value-$sequence",
      aBoolean = null,
      integer = sequence
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = ImmutableValueWithNullableFields::insert,
      bulkInsert = ImmutableValueWithNullableFieldss::insert,
      update = ImmutableValueWithNullableFields::update,
      bulkUpdate = ImmutableValueWithNullableFieldss::update,
      persist = ImmutableValueWithNullableFields::persist,
      bulkPersist = ImmutableValueWithNullableFieldss::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = ImmutableValueWithNullableFields::delete,
      bulkDelete = ImmutableValueWithNullableFieldss::delete,
      deleteTable = ImmutableValueWithNullableFieldss::deleteTable
    )

    override fun updatedValue(value: ImmutableValueWithNullableFields, sequence: Int) = value.copy(
      string = "immutable-value-updated-$sequence",
      aBoolean = sequence % 2 == 0,
      integer = 100 + sequence
    )

    override fun partialNullValue(sequence: Int) = ImmutableValueWithNullableFields(
      id = null,
      string = "immutable-value-null-omitting-$sequence",
      aBoolean = null,
      integer = 200 + sequence
    )

    override fun partialNullUpdatedValue(value: ImmutableValueWithNullableFields, sequence: Int) = value.copy(
      string = null,
      aBoolean = null,
      integer = 300 + sequence
    )

    override fun expectedAfterNullOmittingUpdate(
      existing: ImmutableValueWithNullableFields,
      value: ImmutableValueWithNullableFields
    ) = existing.copy(integer = value.integer)

    override fun allNullValueForMissingRow() = ImmutableValueWithNullableFields(
      id = null,
      string = null,
      aBoolean = null,
      integer = null
    )

    override fun allNullValueForExistingRow(value: ImmutableValueWithNullableFields) = value.copy(
      string = null,
      aBoolean = null,
      integer = null
    )

    override fun expectedAfterAllNullBulkInsert(actual: List<ImmutableValueWithNullableFields>) = actual.map { value ->
      ImmutableValueWithNullableFields(
        id = value.id,
        string = null,
        aBoolean = null,
        integer = null
      )
    }

    override fun expectedAfterInsert(
      value: ImmutableValueWithNullableFields,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun expectedAfterBulkInsert(
      values: List<ImmutableValueWithNullableFields>,
      actual: List<ImmutableValueWithNullableFields>
    ) = actual.map { persisted ->
      values
        .single { value -> value.string == persisted.string && value.integer == persisted.integer }
        .copy(id = persisted.id)
    }

    override fun toString() = name
  }

  private object ImmutableValueWithFieldsCase :
    StandardBulkPersistModelCase<ImmutableValueWithFields>,
    StandardBulkDeleteModelCase<ImmutableValueWithFields> {
    override val name = "ImmutableValueWithFields"
    override val table = IMMUTABLE_VALUE_WITH_FIELDS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ImmutableValueWithFields(
      id = null,
      stringValue = "transformed-value-$sequence",
      aBoolean = true,
      integer = 17 + sequence,
      aDouble = 2.5 + sequence,
      aShort = 3,
      transformableObject = TransformableObject(value = 23 + sequence)
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = ImmutableValueWithFields::insert,
      bulkInsert = ImmutableValueWithFieldss::insert,
      update = ImmutableValueWithFields::update,
      bulkUpdate = ImmutableValueWithFieldss::update,
      persist = ImmutableValueWithFields::persist,
      bulkPersist = ImmutableValueWithFieldss::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = ImmutableValueWithFields::delete,
      bulkDelete = ImmutableValueWithFieldss::delete,
      deleteTable = ImmutableValueWithFieldss::deleteTable
    )

    override fun updatedValue(value: ImmutableValueWithFields, sequence: Int) = value.copy(
      stringValue = "transformed-updated-$sequence",
      aBoolean = sequence % 2 != 0,
      integer = 100 + sequence,
      aDouble = 10.5 + sequence,
      aShort = (10 + sequence).toShort(),
      transformableObject = TransformableObject(value = 100 + sequence)
    )

    override fun expectedAfterInsert(
      value: ImmutableValueWithFields,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun expectedAfterBulkInsert(
      values: List<ImmutableValueWithFields>,
      actual: List<ImmutableValueWithFields>
    ) = actual.map { persisted ->
      values
        .single { it.stringValue == persisted.stringValue }
        .copy(id = persisted.id)
    }

    override fun toString() = name
  }

  private object CustomColumnEntityCase :
    StandardBulkPersistModelCase<CustomColumnEntity>,
    StandardBulkDeleteModelCase<CustomColumnEntity> {
    override val name = "CustomColumnEntity"
    override val table = CUSTOM_COLUMN_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = CustomColumnEntity(
      id = null,
      value = "custom-column-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = CustomColumnEntity::insert,
      bulkInsert = CustomColumnEntitys::insert,
      update = CustomColumnEntity::update,
      bulkUpdate = CustomColumnEntitys::update,
      persist = CustomColumnEntity::persist,
      bulkPersist = CustomColumnEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = CustomColumnEntity::delete,
      bulkDelete = CustomColumnEntitys::delete,
      deleteTable = CustomColumnEntitys::deleteTable
    )

    override fun updatedValue(value: CustomColumnEntity, sequence: Int) = value.copy(
      value = "custom-column-updated-$sequence"
    )

    override fun expectedAfterInsert(value: CustomColumnEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SelectiveColumnsEntityCase :
    StandardBulkPersistModelCase<SelectiveColumnsEntity>,
    StandardBulkDeleteModelCase<SelectiveColumnsEntity> {
    override val name = "SelectiveColumnsEntity"
    override val table = SELECTIVE_COLUMNS_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SelectiveColumnsEntity().apply {
      persistedValue = "selective-persisted-$sequence"
      transientValue = "selective-transient-$sequence"
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = SelectiveColumnsEntity::insert,
      bulkInsert = SelectiveColumnsEntitys::insert,
      update = SelectiveColumnsEntity::update,
      bulkUpdate = SelectiveColumnsEntitys::update,
      persist = SelectiveColumnsEntity::persist,
      bulkPersist = SelectiveColumnsEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = SelectiveColumnsEntity::delete,
      bulkDelete = SelectiveColumnsEntitys::delete,
      deleteTable = SelectiveColumnsEntitys::deleteTable
    )

    override fun updatedValue(value: SelectiveColumnsEntity, sequence: Int) = SelectiveColumnsEntity().apply {
      id = value.id
      persistedValue = "selective-updated-$sequence"
      transientValue = "selective-transient-updated-$sequence"
    }

    override fun expectedAfterUpdate(value: SelectiveColumnsEntity) = SelectiveColumnsEntity().apply {
      id = value.id
      persistedValue = value.persistedValue
    }

    override fun expectedAfterInsert(
      value: SelectiveColumnsEntity,
      result: EntityInsertResult.Inserted
    ) = SelectiveColumnsEntity().apply {
      id = checkNotNull(result.rowId)
      persistedValue = value.persistedValue
    }

    override fun expectedAfterBulkInsert(
      values: List<SelectiveColumnsEntity>,
      actual: List<SelectiveColumnsEntity>
    ) = actual.map { persisted ->
      values
        .single { it.persistedValue == persisted.persistedValue }
        .let { value ->
          SelectiveColumnsEntity().apply {
            id = persisted.id
            persistedValue = value.persistedValue
          }
        }
    }

    override fun toString() = name
  }

  private object EntityWithIgnoredValueCase :
    StandardBulkPersistModelCase<EntityWithIgnoredValue>,
    StandardBulkDeleteModelCase<EntityWithIgnoredValue> {
    override val name = "EntityWithIgnoredValue"
    override val table = ENTITY_WITH_IGNORED_VALUE
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithIgnoredValue().apply {
      persistedValue = "ignored-persisted-$sequence"
      ignoredValue = "ignored-value-$sequence"
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = EntityWithIgnoredValue::insert,
      bulkInsert = EntityWithIgnoredValues::insert,
      update = EntityWithIgnoredValue::update,
      bulkUpdate = EntityWithIgnoredValues::update,
      persist = EntityWithIgnoredValue::persist,
      bulkPersist = EntityWithIgnoredValues::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EntityWithIgnoredValue::delete,
      bulkDelete = EntityWithIgnoredValues::delete,
      deleteTable = EntityWithIgnoredValues::deleteTable
    )

    override fun updatedValue(value: EntityWithIgnoredValue, sequence: Int) = EntityWithIgnoredValue().apply {
      id = value.id
      persistedValue = "ignored-persisted-updated-$sequence"
      ignoredValue = "ignored-value-updated-$sequence"
    }

    override fun expectedAfterUpdate(value: EntityWithIgnoredValue) = EntityWithIgnoredValue().apply {
      id = value.id
      persistedValue = value.persistedValue
    }

    override fun expectedAfterInsert(
      value: EntityWithIgnoredValue,
      result: EntityInsertResult.Inserted
    ) = EntityWithIgnoredValue().apply {
      id = checkNotNull(result.rowId)
      persistedValue = value.persistedValue
    }

    override fun expectedAfterBulkInsert(
      values: List<EntityWithIgnoredValue>,
      actual: List<EntityWithIgnoredValue>
    ) = actual.map { persisted ->
      values
        .single { it.persistedValue == persisted.persistedValue }
        .let { value ->
          EntityWithIgnoredValue().apply {
            id = persisted.id
            persistedValue = value.persistedValue
          }
        }
    }

    override fun toString() = name
  }

  private object BlobEntityCase :
    StandardBulkPersistModelCase<BlobEntity>,
    StandardBulkDeleteModelCase<BlobEntity> {
    override val name = "BlobEntity"
    override val table = BLOB_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = BlobEntity(
      id = null,
      payload = byteArrayOf(
        sequence.toByte(),
        (sequence + 1).toByte(),
        (sequence + 2).toByte()
      )
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = BlobEntity::insert,
      bulkInsert = BlobEntitys::insert,
      update = BlobEntity::update,
      bulkUpdate = BlobEntitys::update,
      persist = BlobEntity::persist,
      bulkPersist = BlobEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = BlobEntity::delete,
      bulkDelete = BlobEntitys::delete,
      deleteTable = BlobEntitys::deleteTable
    )

    override fun updatedValue(value: BlobEntity, sequence: Int) = BlobEntity(
      id = value.id,
      payload = byteArrayOf(
        sequence.toByte(),
        (sequence + 1).toByte(),
        (sequence + 2).toByte()
      )
    )

    override fun expectedAfterInsert(
      value: BlobEntity,
      result: EntityInsertResult.Inserted
    ) = BlobEntity(
      id = checkNotNull(result.rowId),
      payload = value.payload
    )

    override fun expectedAfterBulkInsert(
      values: List<BlobEntity>,
      actual: List<BlobEntity>
    ) = actual.map { persisted ->
      values
        .single { it.payload.contentEquals(persisted.payload) }
        .let { value ->
          BlobEntity(
            id = persisted.id,
            payload = value.payload
          )
        }
    }

    override fun toString() = name
  }
}
