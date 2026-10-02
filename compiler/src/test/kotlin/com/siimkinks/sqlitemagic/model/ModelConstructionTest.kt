package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import org.junit.jupiter.api.Test

internal class ModelConstructionTest {
  @Test
  fun `constructor construction retains ordered and defaultable property paths`() {
    val idPath = PropertyPath(listOf("id"))
    val namePath = PropertyPath(listOf("name"))
    val notePath = PropertyPath(listOf("note"))

    val actual = mockModelConstruction(
      strategy = PRIMARY_CONSTRUCTOR,
      constructorParameters = listOf(idPath, namePath, notePath),
      defaultableParameters = setOf(namePath, notePath)
    )

    assertThat(actual)
      .isEqualTo(
        ModelConstruction(
          strategy = PRIMARY_CONSTRUCTOR,
          constructorParameters = listOf(idPath, namePath, notePath),
          defaultableParameters = setOf(namePath, notePath)
        )
      )
    assertThat(actual.constructorParameterIndex(idPath))
      .isEqualTo(0)
    assertThat(actual.constructorParameterIndex(notePath))
      .isEqualTo(2)
    assertThat(actual.constructorParameterIndex(PropertyPath(listOf("missing"))))
      .isNull()
  }

  @Test
  fun `ID-only construction follows the accepted construction strategy`() {
    val idPath = PropertyPath(listOf("id"))
    val requiredPath = PropertyPath(listOf("required"))
    val defaultedPath = PropertyPath(listOf("defaulted"))
    val constructorBacked = mockModelConstruction(
      constructorParameters = listOf(idPath, defaultedPath),
      defaultableParameters = setOf(defaultedPath)
    )
    val constructorWithRequiredValue = mockModelConstruction(
      constructorParameters = listOf(idPath, requiredPath)
    )
    val mutable = mockModelConstruction(
      strategy = MUTABLE_PROPERTIES,
      constructorParameters = emptyList()
    )

    assertThat(constructorBacked.canConstructWithOnly(idPath))
      .isTrue()
    assertThat(constructorWithRequiredValue.canConstructWithOnly(idPath))
      .isFalse()
    assertThat(mutable.canConstructWithOnly(idPath))
      .isTrue()
  }

  @Test
  fun `property paths retain nested construction access`() {
    val actual = PropertyPath(listOf("shipping"))
      .child("geo")
      .child("latitude")

    assertThat(actual)
      .isEqualTo(PropertyPath(listOf("shipping", "geo", "latitude")))
  }
}
