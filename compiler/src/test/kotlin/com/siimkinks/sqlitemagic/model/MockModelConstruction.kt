package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR

fun mockModelConstruction(
  strategy: ModelConstructionStrategy = PRIMARY_CONSTRUCTOR,
  constructorParameters: List<PropertyPath> = listOf(mockPropertyPath()),
  defaultableParameters: Set<PropertyPath> = emptySet()
) = ModelConstruction(
  strategy = strategy,
  constructorParameters = constructorParameters,
  defaultableParameters = defaultableParameters
)
