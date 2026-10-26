package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE

internal fun <T> executeSinglePersist(
  modelCase: NullOmittingPersistModelCase<T>,
  value: T,
  terminal: OperationTerminal
) = terminal.select(
  execute = { modelCase.executePersistIgnoringNullValues(value = value) },
  observe = { modelCase.observePersistIgnoringNullValues(value = value) }
)

internal fun <T> executeBulkPersist(
  modelCase: NullOmittingPersistModelCase<T>,
  values: List<T>,
  terminal: OperationTerminal
) = when (terminal) {
  EXECUTE -> assertThat(
    modelCase.executeBulkPersistIgnoringNullValues(values = values)
  ).isTrue()
  OBSERVE -> modelCase
    .observeBulkPersistIgnoringNullValues(values = values)
    .blockingAwait()
}

internal fun <T> nullOmittingSnapshot(
  modelCase: NullOmittingPersistModelCase<T>,
  parents: List<T>
) = DatabaseSnapshot(
  parents = parents,
  related = modelCase.expectedRelatedValues(values = parents)
)
