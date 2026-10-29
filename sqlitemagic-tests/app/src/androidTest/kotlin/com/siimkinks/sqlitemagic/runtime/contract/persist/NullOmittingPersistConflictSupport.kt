package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.rxjava2.observe

internal fun <T> assertIgnoredPersist(
  modelCase: NullOmittingPersistConflictModelCase<T>,
  value: T,
  terminal: OperationTerminal
) {
  val builder = modelCase
    .persist(value = value)
    .ignoreNullValues()
    .conflictAlgorithm(CONFLICT_IGNORE)
  when (terminal) {
    EXECUTE -> assertThat(builder.execute())
      .isEqualTo(EntityPersistResult.Ignored)
    OBSERVE -> builder
      .observe()
      .test()
      .assertResult(EntityPersistResult.Ignored)
  }
}

internal fun <T> assertSuccessfulBulkPersist(
  modelCase: NullOmittingPersistConflictModelCase<T>,
  values: List<T>,
  terminal: OperationTerminal
) {
  val builder = modelCase
    .bulkPersist(values = values)
    .ignoreNullValues()
    .conflictAlgorithm(CONFLICT_IGNORE)
  when (terminal) {
    EXECUTE -> assertThat(builder.execute()).isTrue()
    OBSERVE -> builder
      .observe()
      .test()
      .assertComplete()
  }
}
