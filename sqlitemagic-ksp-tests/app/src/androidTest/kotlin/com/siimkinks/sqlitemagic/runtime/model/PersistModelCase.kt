package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import io.reactivex.Single

interface PersistModelCase<T> : UpdateModelCase<T> {
  fun executePersist(value: T): EntityPersistResult

  fun observePersist(value: T): Single<EntityPersistResult>
}
