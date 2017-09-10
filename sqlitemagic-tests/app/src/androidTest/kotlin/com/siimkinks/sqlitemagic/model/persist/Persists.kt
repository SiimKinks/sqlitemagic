@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.update.newUpdatableRandom

fun newRandomWithNulledColumns(): (TestModel<Any>) -> Any = {
  val model = it as TestModelWithNullableColumns
  val random = model.newRandom()
  model.nullSomeColumns(random)
}

fun newComplexRandomWithNulledColumns(): (TestModel<Any>) -> Any = {
  val model = it as ComplexTestModelWithNullableColumns
  val random = model.newRandom()
  model.nullSomeComplexColumns(random)
}

fun newUpdatableRandomWithNulledColumns(): (TestModel<Any>) -> Any = {
  val newRandom = newUpdatableRandom<Any>().invoke(it)
  (it as NullableColumns<Any>).nullSomeColumns(newRandom)
}

fun <T> newComplexUpdatableRandomWithNulledColumns(): (TestModel<T>) -> T = {
  val newRandom = newUpdatableRandom<T>().invoke(it)
  (it as ComplexNullableColumns<T>).nullSomeComplexColumns(newRandom)
}
