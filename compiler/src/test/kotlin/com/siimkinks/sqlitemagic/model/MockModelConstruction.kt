package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR

internal fun mockModelConstruction(
  constructorParameters: List<PropertyPath> = listOf(mockPropertyPath())
) = ModelConstruction(
  strategy = PRIMARY_CONSTRUCTOR,
  constructorParameters = constructorParameters,
  defaultableParameters = emptySet(),
  nullableParameters = emptySet()
)
