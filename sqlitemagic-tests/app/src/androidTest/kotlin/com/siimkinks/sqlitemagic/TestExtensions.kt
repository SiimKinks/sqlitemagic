package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.fail
import org.junit.Before

interface DefaultConnectionTest {
  @Before
  fun setUp() {
    SqliteMagic.getDefaultConnection().clearData()
  }
}

inline fun <T> createVals(count: Int = 10, createFun: () -> T): MutableList<T> {
  val values = ArrayList<T>(count)
  for (i in 0..count) {
    values.add(createFun())
  }
  return values
}

inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
  try {
    block()
    fail("This block was supposed to throw")
  } catch (e: Throwable) {
    assertWithMessage("Expected exception of type ${T::class.simpleName}, but was ${e::class.simpleName}")
      .that(e is T)
      .isTrue()
    assertThat(e.message).isNotEmpty()
  }
}
