package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.DefaultedPrimaryConstructorTable.Companion.DEFAULTED_PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.InheritedMutableEntityTable.Companion.INHERITED_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.InheritedMutableEntitys
import com.siimkinks.sqlitemagic.MutableBodyEntityTable.Companion.MUTABLE_BODY_ENTITY
import com.siimkinks.sqlitemagic.MutableBodyEntitys
import com.siimkinks.sqlitemagic.MutableNullableCompositeDefaultsTable.Companion.MUTABLE_NULLABLE_COMPOSITE_DEFAULTS
import com.siimkinks.sqlitemagic.MutableNullableDefaultsTable.Companion.MUTABLE_NULLABLE_DEFAULTS
import com.siimkinks.sqlitemagic.NonDataConstructorEntityTable.Companion.NON_DATA_CONSTRUCTOR_ENTITY
import com.siimkinks.sqlitemagic.NonDataConstructorEntitys
import com.siimkinks.sqlitemagic.SecondaryNoArgConstructorTable.Companion.SECONDARY_NO_ARG_CONSTRUCTOR
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.DefaultedPrimaryConstructor
import com.siimkinks.sqlitemagic.fixture.model.InheritedMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.MutableBodyEntity
import com.siimkinks.sqlitemagic.fixture.model.MutableNullableCompositeDefaults
import com.siimkinks.sqlitemagic.fixture.model.MutableNullableDefaults
import com.siimkinks.sqlitemagic.fixture.model.MutableNullableDetails
import com.siimkinks.sqlitemagic.fixture.model.MutableNullableOwner
import com.siimkinks.sqlitemagic.fixture.model.NonDataConstructorEntity
import com.siimkinks.sqlitemagic.fixture.model.SecondaryNoArgConstructor
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardTableDeleteModelCase
import com.siimkinks.sqlitemagic.update

internal object ConstructionModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    NonDataConstructorEntityCase,
    MutableBodyEntityCase,
    InheritedMutableEntityCase,
    SecondaryNoArgConstructorCase,
    DefaultedPrimaryConstructorCase,
    MutableNullableDefaultsCase,
    MutableNullableCompositeDefaultsCase,
  )

  private object NonDataConstructorEntityCase :
    BulkInsertModelCase<NonDataConstructorEntity>,
    StandardTableDeleteModelCase<NonDataConstructorEntity> {
    override val name = "NonDataConstructorEntity"
    override val table = NON_DATA_CONSTRUCTOR_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NonDataConstructorEntity(value = "constructor-$sequence")

    override fun insert(value: NonDataConstructorEntity) = value.insert()

    override fun bulkInsert(values: List<NonDataConstructorEntity>) = NonDataConstructorEntitys.insert(values)

    override fun deleteTable() = NonDataConstructorEntitys.deleteTable()

    override fun expectedAfterInsert(value: NonDataConstructorEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object MutableBodyEntityCase :
    StandardBulkPersistModelCase<MutableBodyEntity>,
    StandardBulkDeleteModelCase<MutableBodyEntity> {
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

    override val deleteBuilders = StandardDeleteBuilders(
      delete = MutableBodyEntity::delete,
      bulkDelete = MutableBodyEntitys::delete,
      deleteTable = MutableBodyEntitys::deleteTable
    )

    override fun expectedAfterInsert(value: MutableBodyEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: MutableBodyEntity, sequence: Int) = value.apply {
      this.value = "mutable-body-updated-$sequence"
    }

    override fun toString() = name
  }

  private object InheritedMutableEntityCase :
    StandardBulkPersistModelCase<InheritedMutableEntity>,
    StandardBulkDeleteModelCase<InheritedMutableEntity> {
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

    override val deleteBuilders = StandardDeleteBuilders(
      delete = InheritedMutableEntity::delete,
      bulkDelete = InheritedMutableEntitys::delete,
      deleteTable = InheritedMutableEntitys::deleteTable
    )

    override fun expectedAfterInsert(value: InheritedMutableEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: InheritedMutableEntity, sequence: Int) = value.apply {
      inheritedValue = "base-updated-$sequence"
      this.value = "child-updated-$sequence"
    }

    override fun toString() = name
  }

  private object SecondaryNoArgConstructorCase : InsertModelCase<SecondaryNoArgConstructor> {
    override val name = "SecondaryNoArgConstructor"
    override val table = SECONDARY_NO_ARG_CONSTRUCTOR
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = SecondaryNoArgConstructor(initialValue = "secondary-$sequence")

    override fun insert(value: SecondaryNoArgConstructor) = value.insert()

    override fun expectedAfterInsert(
      value: SecondaryNoArgConstructor,
      result: EntityInsertResult.Inserted
    ) = value

    override fun toString() = name
  }

  private object DefaultedPrimaryConstructorCase : InsertModelCase<DefaultedPrimaryConstructor> {
    override val name = "DefaultedPrimaryConstructor"
    override val table = DEFAULTED_PRIMARY_CONSTRUCTOR
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = DefaultedPrimaryConstructor(initialValue = "defaulted-$sequence")

    override fun insert(value: DefaultedPrimaryConstructor) = value.insert()

    override fun expectedAfterInsert(
      value: DefaultedPrimaryConstructor,
      result: EntityInsertResult.Inserted
    ) = value

    override fun toString() = name
  }

  private object MutableNullableDefaultsCase : SuccessfulModelProjectionCase<MutableNullableDefaults> {
    override val name = "MutableNullableDefaults"
    override val table = MUTABLE_NULLABLE_DEFAULTS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT
    override val projectionColumns = listOf(
      MUTABLE_NULLABLE_DEFAULTS.ID,
      MUTABLE_NULLABLE_DEFAULTS.LABEL,
      MUTABLE_NULLABLE_DEFAULTS.COUNT
    )

    override fun newValue(sequence: Int) = MutableNullableDefaults().apply {
      label = null
      count = null
    }

    override fun insert(value: MutableNullableDefaults) = value.insert()

    override fun expectedAfterInsert(
      value: MutableNullableDefaults,
      result: EntityInsertResult.Inserted
    ) = MutableNullableDefaults().apply {
      id = checkNotNull(result.rowId)
    }

    override fun expectedAfterProjection(value: MutableNullableDefaults) = value

    override fun toString() = name
  }

  private object MutableNullableCompositeDefaultsCase :
    SuccessfulModelProjectionCase<MutableNullableCompositeDefaults> {
    override val name = "MutableNullableCompositeDefaults"
    override val table = MUTABLE_NULLABLE_COMPOSITE_DEFAULTS
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT
    override val projectionColumns = listOf(
      MUTABLE_NULLABLE_COMPOSITE_DEFAULTS.ID,
      MUTABLE_NULLABLE_COMPOSITE_DEFAULTS.LABEL,
      MUTABLE_NULLABLE_COMPOSITE_DEFAULTS.COUNT,
      MUTABLE_NULLABLE_COMPOSITE_DEFAULTS.OWNER
    )

    override fun newValue(sequence: Int) = MutableNullableCompositeDefaults().apply {
      details = when {
        sequence % 2 == 0 -> null
        else -> MutableNullableDetails().apply {
          label = "details-$sequence"
          count = sequence.toLong()
        }
      }
      owner = null
    }

    override fun insert(value: MutableNullableCompositeDefaults) = value.insert()

    override fun expectedAfterInsert(
      value: MutableNullableCompositeDefaults,
      result: EntityInsertResult.Inserted
    ) = MutableNullableCompositeDefaults().apply {
      id = checkNotNull(result.rowId)
      details = value.details ?: MutableNullableDetails()
      owner = MutableNullableOwner()
    }

    override fun expectedAfterProjection(value: MutableNullableCompositeDefaults) = value

    override fun toString() = name
  }
}
