@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.update.newUpdatableRandom
import java.util.concurrent.TimeUnit

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

class PersistForUpdateDualOperation<T>(
    private val ignoreNullValues: Boolean = false,
    private val persistBuilderCallback: (model: TestModel<T>, testVal: T) -> EntityPersistBuilder
) : DualOperation<T, T, Boolean> {
  override fun executeTest(model: TestModel<T>, testVal: T): Boolean =
      persistBuilderCallback(model, testVal)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .execute() != -1L

  override fun observeTest(model: TestModel<T>, testVal: T): Boolean =
      persistBuilderCallback(model, testVal)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .observe()
          .blockingGet() != -1L
}

class BulkPersistDualOperation<T>(
    private val ignoreNullValues: Boolean = false,
    private val persistBuilderCallback: (model: TestModel<T>, testVals: List<T>) -> EntityBulkPersistBuilder
) : DualOperation<List<T>, T, Boolean> {
  override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean =
      persistBuilderCallback(model, testVal)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .execute()

  override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean =
      persistBuilderCallback(model, testVal)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .observe()
          .blockingGet(1, TimeUnit.SECONDS) == null
}