package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EmbeddedValueEntityTable.Companion.EMBEDDED_VALUE_ENTITY
import com.siimkinks.sqlitemagic.EmbeddedValueEntitys
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedCoordinates
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedDetails
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedValueEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.MissingRequiredProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.update

internal object EmbeddedModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    EmbeddedValueEntityWithOptionalValueCase,
    EmbeddedValueEntityWithoutOptionalValueCase,
  )

  private object EmbeddedValueEntityWithOptionalValueCase :
    StandardNullOmittingPersistModelCase<EmbeddedValueEntity>,
    StandardBulkDeleteModelCase<EmbeddedValueEntity>,
    SuccessfulModelProjectionCase<EmbeddedValueEntity>,
    MissingRequiredProjectionCase<EmbeddedValueEntity> {
    override val name = "EmbeddedValueEntityWithOptionalValue"
    override val table = EMBEDDED_VALUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override val projectionColumns = listOf(
      EMBEDDED_VALUE_ENTITY.ID,
      EMBEDDED_VALUE_ENTITY.REQUIRED_LABEL,
      EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LATITUDE,
      EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LONGITUDE,
      EMBEDDED_VALUE_ENTITY.REQUIRED_TRANSFORMED_VALUE
    )
    override val missingRequiredProjectionColumns = listOf(
      EMBEDDED_VALUE_ENTITY.ID,
      EMBEDDED_VALUE_ENTITY.REQUIRED_LABEL,
      EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LATITUDE,
      EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LONGITUDE,
      EMBEDDED_VALUE_ENTITY.REQUIRED_TRANSFORMED_VALUE,
      EMBEDDED_VALUE_ENTITY.OPTIONAL_LABEL
    )
    override val expectedSQLExceptionMessage =
      """Selected columns did not contain table "embedded_value_entity" required column "optional_coordinates_latitude""""

    override fun newValue(sequence: Int) = EmbeddedValueEntity(
      id = null,
      requiredDetails = EmbeddedDetails(
        label = "embedded-required-$sequence",
        coordinates = EmbeddedCoordinates(
          latitude = 10.0 + sequence,
          longitude = 20.0 + sequence
        ),
        transformedValue = TransformableObject(value = 100 + sequence)
      ),
      optionalDetails = EmbeddedDetails(
        label = "embedded-optional-$sequence",
        coordinates = EmbeddedCoordinates(
          latitude = 30.0 + sequence,
          longitude = 40.0 + sequence
        ),
        transformedValue = TransformableObject(value = 200 + sequence)
      )
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = EmbeddedValueEntity::insert,
      bulkInsert = EmbeddedValueEntitys::insert,
      update = EmbeddedValueEntity::update,
      bulkUpdate = EmbeddedValueEntitys::update,
      persist = EmbeddedValueEntity::persist,
      bulkPersist = EmbeddedValueEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EmbeddedValueEntity::delete,
      bulkDelete = EmbeddedValueEntitys::delete,
      deleteTable = EmbeddedValueEntitys::deleteTable
    )

    override fun updatedValue(value: EmbeddedValueEntity, sequence: Int) = value.copy(
      requiredDetails = value.requiredDetails
        .updated(
          sequence = sequence,
          prefix = "required"
        ),
      optionalDetails = value.optionalDetails
        ?.updated(
          sequence = sequence,
          prefix = "optional"
        )
    )

    override fun partialNullValue(sequence: Int) = newValue(sequence = sequence)
      .copy(optionalDetails = null)

    override fun partialNullUpdatedValue(value: EmbeddedValueEntity, sequence: Int) = value.copy(
      requiredDetails = value.requiredDetails
        .updated(
          sequence = sequence,
          prefix = "required-null-omitting"
        ),
      optionalDetails = null
    )

    override fun expectedAfterNullOmittingUpdate(
      existing: EmbeddedValueEntity,
      value: EmbeddedValueEntity
    ) = value.copy(optionalDetails = existing.optionalDetails)

    override fun expectedAfterInsert(
      value: EmbeddedValueEntity,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun expectedAfterProjection(value: EmbeddedValueEntity) = value.copy(optionalDetails = null)

    override fun expectedAfterBulkInsert(
      values: List<EmbeddedValueEntity>,
      actual: List<EmbeddedValueEntity>
    ) = actual.map { persisted ->
      values
        .single { it.requiredDetails.label == persisted.requiredDetails.label }
        .copy(id = persisted.id)
    }

    override fun toString() = name
  }

  private object EmbeddedValueEntityWithoutOptionalValueCase :
    StandardBulkPersistModelCase<EmbeddedValueEntity>,
    StandardBulkDeleteModelCase<EmbeddedValueEntity> {
    override val name = "EmbeddedValueEntityWithoutOptionalValue"
    override val table = EMBEDDED_VALUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EmbeddedValueEntity(
      id = null,
      requiredDetails = EmbeddedDetails(
        label = "embedded-required-$sequence",
        coordinates = EmbeddedCoordinates(
          latitude = 10.0 + sequence,
          longitude = 20.0 + sequence
        ),
        transformedValue = TransformableObject(value = 100 + sequence)
      ),
      optionalDetails = null
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = EmbeddedValueEntity::insert,
      bulkInsert = EmbeddedValueEntitys::insert,
      update = EmbeddedValueEntity::update,
      bulkUpdate = EmbeddedValueEntitys::update,
      persist = EmbeddedValueEntity::persist,
      bulkPersist = EmbeddedValueEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EmbeddedValueEntity::delete,
      bulkDelete = EmbeddedValueEntitys::delete,
      deleteTable = EmbeddedValueEntitys::deleteTable
    )

    override fun updatedValue(value: EmbeddedValueEntity, sequence: Int) = value.copy(
      requiredDetails = value.requiredDetails
        .updated(
          sequence = sequence,
          prefix = "required"
        ),
      optionalDetails = value.optionalDetails
        ?.updated(
          sequence = sequence,
          prefix = "optional"
        )
    )

    override fun expectedAfterInsert(
      value: EmbeddedValueEntity,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun expectedAfterBulkInsert(
      values: List<EmbeddedValueEntity>,
      actual: List<EmbeddedValueEntity>
    ) = actual.map { persisted ->
      values
        .single { it.requiredDetails.label == persisted.requiredDetails.label }
        .copy(id = persisted.id)
    }

    override fun toString() = name
  }

  private fun EmbeddedDetails.updated(sequence: Int, prefix: String) = copy(
    label = "$prefix-label-updated-$sequence",
    coordinates = coordinates
      .copy(
        latitude = coordinates.latitude + 100 + sequence,
        longitude = coordinates.longitude + 100 + sequence
      ),
    transformedValue = TransformableObject(value = transformedValue.value + 100 + sequence)
  )
}
