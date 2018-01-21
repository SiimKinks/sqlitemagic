package com.siimkinks.sqlitemagic.submodule

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.siimkinks.sqlitemagic.SubmoduleGeneratedClassesManager.*
import com.siimkinks.sqlitemagic.SubmoduleTransformableObjectColumn
import com.siimkinks.sqlitemagic.internal.StringArraySet
import org.junit.Test

class SubmoduleGenClassesManagerTest {
  @Test
  fun clearDataReturnsAllModuleTables() {
    val allChangedTables = clearData(mock())
    assertThat(allChangedTables.size).isEqualTo(getNrOfTables(null))
    assertThat(allChangedTables).isEqualTo(StringArraySet(arrayOf("immutable_value_with_nullable_fields", "immutable_value")))
  }

  @Test
  fun tableCountIsNotChangedForAllTableCount() {
    assertThat(getNrOfTables(null))
        .isEqualTo(2)
  }

  @Test
  fun tableCountIsNotChangedForNativeModule() {
    assertThat(getNrOfTables(""))
        .isEqualTo(2)
  }

  @Test
  fun tableCountIsNotChangedForNativeModuleWithSubmoduleName() {
    assertThat(getNrOfTables("Submodule"))
        .isEqualTo(2)
  }

  @Test
  fun columnForDefaultTransformerValueNotFound() {
    val column = columnForValueOrNull(Boolean::class.java.canonicalName, true)
    assertThat(column).isNull()
  }

  @Test
  fun columnForNativeTransformerValue() {
    val value = SubmoduleTransformableObject(0)
    val column = columnForValueOrNull(value.javaClass.canonicalName, value)
    assertThat(column).isInstanceOf(SubmoduleTransformableObjectColumn::class.java)
  }

  @Test
  fun columnForValueWithoutTransformer() {
    val column = columnForValueOrNull(String::class.java.canonicalName, "")
    assertThat(column).isNull()
  }
}