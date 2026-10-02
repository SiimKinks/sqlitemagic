package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class EmbeddedValueEntity(
  @Id val id: Long?,
  @Embedded(prefix = "required_") val requiredDetails: EmbeddedDetails,
  @Embedded(prefix = "optional_") val optionalDetails: EmbeddedDetails?
)

data class EmbeddedDetails(
  val label: String,
  @Embedded(prefix = "coordinates_") val coordinates: EmbeddedCoordinates,
  val transformedValue: TransformableObject
)

data class EmbeddedCoordinates(
  val latitude: Double,
  val longitude: Double
)
