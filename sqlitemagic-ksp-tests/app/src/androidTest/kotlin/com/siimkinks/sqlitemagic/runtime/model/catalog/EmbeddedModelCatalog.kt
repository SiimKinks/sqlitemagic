package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EmbeddedValueEntityTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedCoordinates
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedDetails
import com.siimkinks.sqlitemagic.fixture.model.EmbeddedValueEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object EmbeddedModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    EmbeddedValueEntityWithOptionalValueCase,
    EmbeddedValueEntityWithoutOptionalValueCase,
  )

  private object EmbeddedValueEntityWithOptionalValueCase : InsertModelCase<EmbeddedValueEntity> {
    override val name = "EmbeddedValueEntityWithOptionalValue"
    override val table = EmbeddedValueEntityTable.EMBEDDED_VALUE_ENTITY
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
      optionalDetails = EmbeddedDetails(
        label = "embedded-optional-$sequence",
        coordinates = EmbeddedCoordinates(
          latitude = 30.0 + sequence,
          longitude = 40.0 + sequence
        ),
        transformedValue = TransformableObject(value = 200 + sequence)
      )
    )

    override fun insert(value: EmbeddedValueEntity) = value.insert()

    override fun expectedAfterInsert(
      value: EmbeddedValueEntity,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun toString() = name
  }

  private object EmbeddedValueEntityWithoutOptionalValueCase : InsertModelCase<EmbeddedValueEntity> {
    override val name = "EmbeddedValueEntityWithoutOptionalValue"
    override val table = EmbeddedValueEntityTable.EMBEDDED_VALUE_ENTITY
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

    override fun insert(value: EmbeddedValueEntity) = value.insert()

    override fun expectedAfterInsert(
      value: EmbeddedValueEntity,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun toString() = name
  }
}
