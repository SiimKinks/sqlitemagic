package com.siimkinks.sqlitemagic.model.insert

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.model.TestModel
import com.siimkinks.sqlitemagic.model.TestModelWithUniqueColumn
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import com.siimkinks.sqlitemagic.model.insertNewRandom
import java.util.concurrent.atomic.AtomicInteger

fun <T> assertInsertSuccess(): (TestModel<T>, T, Long) -> Unit = { model, testVal, id ->
  assertTableCount(1L, model.table)
  assertThat(id).isNotEqualTo(-1)
  val dbVal = Select.from(model.table).queryDeep().takeFirst().execute()
  assertThat(dbVal).isNotNull()
  assertThat(model.valsAreEqual(testVal, dbVal!!)).isTrue()
}

fun <T> assertEarlyUnsubscribeFromInsertRollbackedAllValues(): (TestModel<T>, List<T>, AtomicInteger) -> Unit = { model, _, eventsCount ->
  val countInDb = Select
      .from(model.table)
      .count()
      .execute()
  assertThat(countInDb).isEqualTo(0L)
  assertThat(eventsCount.get()).isEqualTo(0)
}

fun <T> assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork(): (TestModel<T>, List<T>, AtomicInteger) -> Unit = { model, _, eventsCount ->
  val countInDb = Select
      .from(model.table)
      .count()
      .execute()
  assertThat(countInDb).isGreaterThan(0L)
  assertThat(countInDb).isLessThan(500)
  assertThat(eventsCount.get()).isEqualTo(0)
}

fun <T> newFailingByUniqueConstraint(): (TestModel<T>) -> T = {
  val model = it as TestModelWithUniqueColumn
  val (v1) = insertNewRandom(model)
  val newRandom = model.newRandom()
  (model).transferUniqueVal(v1, newRandom)
}
