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
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.PersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
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

  internal val emptyBulkUpdateCase: BulkUpdateModelCase<SimpleMutableEntity> = SimpleMutableEntityCase

  private object SimpleMutableEntityCase :
    BulkInsertModelCase<SimpleMutableEntity>,
    BulkUpdateModelCase<SimpleMutableEntity>,
    PersistModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntity"
    override val table = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = null,
      value = "simple-mutable-entity-$sequence",
      boxedBoolean = null,
      primitiveBoolean = true
    )

    override fun insert(value: SimpleMutableEntity) = value.insert()

    override fun bulkInsert(values: List<SimpleMutableEntity>) = SimpleMutableEntitys.insert(values)

    override fun updatedValue(value: SimpleMutableEntity, sequence: Int) = value.copy(
      value = "simple-mutable-entity-updated-$sequence",
      boxedBoolean = sequence % 2 == 0,
      primitiveBoolean = sequence % 2 != 0
    )

    override fun executeUpdate(
      value: SimpleMutableEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: SimpleMutableEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<SimpleMutableEntity>) = SimpleMutableEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<SimpleMutableEntity>) = SimpleMutableEntitys
      .update(values)
      .observe()

    override fun executePersist(value: SimpleMutableEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: SimpleMutableEntity) = value
      .persist()
      .observe()

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SimpleMutableEntityWithPresetAutoIdCase :
    BulkInsertModelCase<SimpleMutableEntity>,
    BulkUpdateModelCase<SimpleMutableEntity>,
    PersistModelCase<SimpleMutableEntity> {
    override val name = "SimpleMutableEntityWithPresetAutoId"
    override val table = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SimpleMutableEntity(
      id = 9001L,
      value = "preset-simple-mutable-entity-$sequence",
      boxedBoolean = true,
      primitiveBoolean = false
    )

    override fun insert(value: SimpleMutableEntity) = value.insert()

    override fun bulkInsert(values: List<SimpleMutableEntity>) = SimpleMutableEntitys.insert(values)

    override fun updatedValue(value: SimpleMutableEntity, sequence: Int) = value.copy(
      value = "preset-simple-mutable-entity-updated-$sequence",
      boxedBoolean = sequence % 2 != 0,
      primitiveBoolean = sequence % 2 == 0
    )

    override fun executeUpdate(
      value: SimpleMutableEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: SimpleMutableEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<SimpleMutableEntity>) = SimpleMutableEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<SimpleMutableEntity>) = SimpleMutableEntitys
      .update(values)
      .observe()

    override fun executePersist(value: SimpleMutableEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: SimpleMutableEntity) = value
      .persist()
      .observe()

    override fun expectedAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun verifyAfterInsert(value: SimpleMutableEntity, result: EntityInsertResult.Inserted) {
      check(value.id != 9001L) { "Preset SimpleMutableEntity ID must be replaced by the generated row ID" }
      check(value.id == result.rowId) { "Preset SimpleMutableEntity ID must equal the inserted row ID" }
    }

    override fun toString() = name
  }

  private object ImmutableValueWithNullableFieldsCase :
    BulkInsertModelCase<ImmutableValueWithNullableFields>,
    BulkUpdateModelCase<ImmutableValueWithNullableFields>,
    PersistModelCase<ImmutableValueWithNullableFields> {
    override val name = "ImmutableValueWithNullableFields"
    override val table = IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = ImmutableValueWithNullableFields(
      id = null,
      string = "immutable-value-$sequence",
      aBoolean = null,
      integer = sequence
    )

    override fun insert(value: ImmutableValueWithNullableFields) = value.insert()

    override fun bulkInsert(values: List<ImmutableValueWithNullableFields>) =
      ImmutableValueWithNullableFieldss.insert(values)

    override fun updatedValue(value: ImmutableValueWithNullableFields, sequence: Int) = value.copy(
      string = "immutable-value-updated-$sequence",
      aBoolean = sequence % 2 == 0,
      integer = 100 + sequence
    )

    override fun executeUpdate(
      value: ImmutableValueWithNullableFields,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: ImmutableValueWithNullableFields,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<ImmutableValueWithNullableFields>) = ImmutableValueWithNullableFieldss
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<ImmutableValueWithNullableFields>) = ImmutableValueWithNullableFieldss
      .update(values)
      .observe()

    override fun executePersist(value: ImmutableValueWithNullableFields) = value
      .persist()
      .execute()

    override fun observePersist(value: ImmutableValueWithNullableFields) = value
      .persist()
      .observe()

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
    BulkInsertModelCase<ImmutableValueWithFields>,
    BulkUpdateModelCase<ImmutableValueWithFields>,
    PersistModelCase<ImmutableValueWithFields> {
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

    override fun insert(value: ImmutableValueWithFields) = value.insert()

    override fun bulkInsert(values: List<ImmutableValueWithFields>) = ImmutableValueWithFieldss.insert(values)

    override fun updatedValue(value: ImmutableValueWithFields, sequence: Int) = value.copy(
      stringValue = "transformed-updated-$sequence",
      aBoolean = sequence % 2 != 0,
      integer = 100 + sequence,
      aDouble = 10.5 + sequence,
      aShort = (10 + sequence).toShort(),
      transformableObject = TransformableObject(value = 100 + sequence)
    )

    override fun executeUpdate(
      value: ImmutableValueWithFields,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: ImmutableValueWithFields,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<ImmutableValueWithFields>) = ImmutableValueWithFieldss
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<ImmutableValueWithFields>) = ImmutableValueWithFieldss
      .update(values)
      .observe()

    override fun executePersist(value: ImmutableValueWithFields) = value
      .persist()
      .execute()

    override fun observePersist(value: ImmutableValueWithFields) = value
      .persist()
      .observe()

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
    BulkInsertModelCase<CustomColumnEntity>,
    BulkUpdateModelCase<CustomColumnEntity>,
    PersistModelCase<CustomColumnEntity> {
    override val name = "CustomColumnEntity"
    override val table = CUSTOM_COLUMN_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = CustomColumnEntity(
      id = null,
      value = "custom-column-$sequence"
    )

    override fun insert(value: CustomColumnEntity) = value.insert()

    override fun bulkInsert(values: List<CustomColumnEntity>) = CustomColumnEntitys.insert(values)

    override fun updatedValue(value: CustomColumnEntity, sequence: Int) = value.copy(
      value = "custom-column-updated-$sequence"
    )

    override fun executeUpdate(
      value: CustomColumnEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: CustomColumnEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<CustomColumnEntity>) = CustomColumnEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<CustomColumnEntity>) = CustomColumnEntitys
      .update(values)
      .observe()

    override fun executePersist(value: CustomColumnEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: CustomColumnEntity) = value
      .persist()
      .observe()

    override fun expectedAfterInsert(value: CustomColumnEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object SelectiveColumnsEntityCase :
    BulkInsertModelCase<SelectiveColumnsEntity>,
    BulkUpdateModelCase<SelectiveColumnsEntity>,
    PersistModelCase<SelectiveColumnsEntity> {
    override val name = "SelectiveColumnsEntity"
    override val table = SELECTIVE_COLUMNS_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SelectiveColumnsEntity().apply {
      persistedValue = "selective-persisted-$sequence"
      transientValue = "selective-transient-$sequence"
    }

    override fun insert(value: SelectiveColumnsEntity) = value.insert()

    override fun bulkInsert(values: List<SelectiveColumnsEntity>) = SelectiveColumnsEntitys.insert(values)

    override fun updatedValue(value: SelectiveColumnsEntity, sequence: Int) = SelectiveColumnsEntity().apply {
      id = value.id
      persistedValue = "selective-updated-$sequence"
      transientValue = "selective-transient-updated-$sequence"
    }

    override fun executeUpdate(
      value: SelectiveColumnsEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: SelectiveColumnsEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<SelectiveColumnsEntity>) = SelectiveColumnsEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<SelectiveColumnsEntity>) = SelectiveColumnsEntitys
      .update(values)
      .observe()

    override fun executePersist(value: SelectiveColumnsEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: SelectiveColumnsEntity) = value
      .persist()
      .observe()

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
    BulkInsertModelCase<EntityWithIgnoredValue>,
    BulkUpdateModelCase<EntityWithIgnoredValue>,
    PersistModelCase<EntityWithIgnoredValue> {
    override val name = "EntityWithIgnoredValue"
    override val table = ENTITY_WITH_IGNORED_VALUE
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithIgnoredValue().apply {
      persistedValue = "ignored-persisted-$sequence"
      ignoredValue = "ignored-value-$sequence"
    }

    override fun insert(value: EntityWithIgnoredValue) = value.insert()

    override fun bulkInsert(values: List<EntityWithIgnoredValue>) = EntityWithIgnoredValues.insert(values)

    override fun updatedValue(value: EntityWithIgnoredValue, sequence: Int) = EntityWithIgnoredValue().apply {
      id = value.id
      persistedValue = "ignored-persisted-updated-$sequence"
      ignoredValue = "ignored-value-updated-$sequence"
    }

    override fun executeUpdate(
      value: EntityWithIgnoredValue,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: EntityWithIgnoredValue,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<EntityWithIgnoredValue>) = EntityWithIgnoredValues
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<EntityWithIgnoredValue>) = EntityWithIgnoredValues
      .update(values)
      .observe()

    override fun executePersist(value: EntityWithIgnoredValue) = value
      .persist()
      .execute()

    override fun observePersist(value: EntityWithIgnoredValue) = value
      .persist()
      .observe()

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
    BulkInsertModelCase<BlobEntity>,
    BulkUpdateModelCase<BlobEntity>,
    PersistModelCase<BlobEntity> {
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

    override fun insert(value: BlobEntity) = value.insert()

    override fun bulkInsert(values: List<BlobEntity>) = BlobEntitys.insert(values)

    override fun updatedValue(value: BlobEntity, sequence: Int) = BlobEntity(
      id = value.id,
      payload = byteArrayOf(
        sequence.toByte(),
        (sequence + 1).toByte(),
        (sequence + 2).toByte()
      )
    )

    override fun executeUpdate(
      value: BlobEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: BlobEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(values: List<BlobEntity>) = BlobEntitys
      .update(values)
      .execute()

    override fun observeBulkUpdate(values: List<BlobEntity>) = BlobEntitys
      .update(values)
      .observe()

    override fun executePersist(value: BlobEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: BlobEntity) = value
      .persist()
      .observe()

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
