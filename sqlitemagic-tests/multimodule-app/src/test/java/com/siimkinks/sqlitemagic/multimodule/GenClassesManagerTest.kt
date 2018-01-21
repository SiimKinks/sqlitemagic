package com.siimkinks.sqlitemagic.multimodule

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.GeneratedClassesManager.*
import com.siimkinks.sqlitemagic.internal.StringArraySet
import com.siimkinks.sqlitemagic.submodule.SubmoduleTransformableObject
import org.junit.Test

class GenClassesManagerTest {
  @Test
  fun clearDataReturnsAllDatabaseTables() {
    val allChangedTables = clearData(mock())
    assertThat(allChangedTables.size).isEqualTo(getNrOfTables(null))
    assertThat(allChangedTables).isEqualTo(StringArraySet(arrayOf(
        "immutable_value_with_nullable_fields",
        "immutable_value",
        "author"
    )))
  }

  @Test
  fun databaseNameFromAnnotation() {
    assertThat(getDbName())
        .isEqualTo("multimodule.db")
  }

  @Test
  fun dbVersionFromAnnotation() {
    assertThat(getDbVersion())
        .isEqualTo(2)
  }

  @Test
  fun tableCountIsAddedForAllTableCount() {
    assertThat(getNrOfTables(null))
        .isEqualTo(3)
  }

  @Test
  fun tableCountOfNativeModule() {
    assertThat(getNrOfTables(""))
        .isEqualTo(1)
  }

  @Test
  fun tableCountOfSubmodule() {
    assertThat(getNrOfTables("Submodule"))
        .isEqualTo(2)
  }

  @Test
  fun getSubmoduleNames() {
    assertThat(GeneratedClassesManager.getSubmoduleNames())
        .isEqualTo(arrayOf("Submodule"))
  }

  @Test
  fun columnForDefaultTransformerValue() {
    val column = columnForValue(true)
    assertThat(column).isInstanceOf(BooleanColumn::class.java)
  }

  @Test
  fun columnForNativeModuleTransformerValue() {
    val column = columnForValue(TransformableObject(0))
    assertThat(column).isInstanceOf(TransformableObjectColumn::class.java)
  }

  @Test
  fun columnForSubmoduleTransformerValue() {
    val column = columnForValue(SubmoduleTransformableObject(0))
    assertThat(column).isInstanceOf(SubmoduleTransformableObjectColumn::class.java)
  }

  @Test
  fun columnForValueWithoutTransformer() {
    val column = columnForValue(Author.newRandom())
    assertThat(column).isInstanceOf(Column::class.java)
  }
}