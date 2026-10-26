package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR

internal enum class ModelConstructionStrategy {
  PRIMARY_CONSTRUCTOR,
  MUTABLE_PROPERTIES
}

internal data class ModelConstruction(
  val strategy: ModelConstructionStrategy,
  val constructorParameters: List<PropertyPath>,
  val defaultableParameters: Set<PropertyPath>
) {
  fun constructorParameterIndex(property: PropertyPath) = constructorParameters
    .indexOf(property)
    .takeUnless { it == -1 }

  fun canConstructWithOnly(property: PropertyPath) = when (strategy) {
    MUTABLE_PROPERTIES -> true
    PRIMARY_CONSTRUCTOR -> constructorParameters.all { parameter ->
      parameter == property || parameter in defaultableParameters
    }
  }
}
