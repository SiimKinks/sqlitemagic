package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveOperation

interface PersistModelCase<T> : UpdateModelCase<T> {
  fun executePersist(value: T): EntityPersistResult

  fun observePersist(
    adapter: ReactiveAdapter,
    value: T
  ): ReactiveOperation<EntityPersistResult>
}
