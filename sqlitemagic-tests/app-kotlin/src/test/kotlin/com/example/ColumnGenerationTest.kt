package com.example

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.model.Author
import com.siimkinks.sqlitemagic.model.Magazine
import com.siimkinks.sqlitemagic.model.immutable.*
import org.junit.Test
import java.lang.reflect.Modifier

class ColumnGenerationTest {
  @Test
  fun authorColumns() {
    assertThat(countNonStaticFields<Author>())
        .isEqualTo(countNonStaticFields<AuthorTable>())
  }

  @Test
  fun magazineColumns() {
    assertThat(countNonStaticFields<Magazine>())
        .isEqualTo(countNonStaticFields<MagazineTable>())
  }

  @Test
  fun complexObjectColumns() {
    assertThat(countNonStaticFields<ComplexObject>())
        .isEqualTo(countNonStaticFields<ComplexObjectTable>())
  }

  @Test
  fun complexObjectWithUniqueColumnColumns() {
    assertThat(countNonStaticFields<ComplexObjectWithUniqueColumn>())
        .isEqualTo(countNonStaticFields<ComplexObjectWithUniqueColumnTable>())
  }

  @Test
  fun complexObjectWithSameLeafsColumns() {
    assertThat(countNonStaticFields<ComplexObjectWithSameLeafs>())
        .isEqualTo(countNonStaticFields<ComplexObjectWithSameLeafsTable>())
  }

  @Test
  fun childObjectColumns() {
    assertThat(countNonStaticFields<ChildObject>())
        .isEqualTo(countNonStaticFields<ChildObjectTable>())
  }

  @Test
  fun immutableValueColumns() {
    assertThat(countNonStaticFields<ImmutableValue>())
        .isEqualTo(countNonStaticFields<ImmutableValueTable>())
  }

  @Test
  fun immutableValueWithFieldsColumns() {
    assertThat(countNonStaticFields<ImmutableValueWithFields>())
        .isEqualTo(countNonStaticFields<ImmutableValueWithFieldsTable>())
  }

  @Test
  fun immutableValueWithNullableFieldsColumns() {
    assertThat(countNonStaticFields<ImmutableValueWithNullableFields>())
        .isEqualTo(countNonStaticFields<ImmutableValueWithNullableFieldsTable>())
  }

  private inline fun <reified T> countNonStaticFields(): Int = T::class.java
      .declaredFields
      .asSequence()
      .filter { it.modifiers.and(Modifier.STATIC) == 0 }
      .count()
}