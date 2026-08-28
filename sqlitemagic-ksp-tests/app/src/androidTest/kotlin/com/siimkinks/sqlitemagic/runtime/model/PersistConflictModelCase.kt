package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder

interface PersistConflictModelCase<T> : InsertModelCase<T> {
  fun persist(value: T): EntityPersistBuilder

  fun valueWithInsertConflict(existing: T, sequence: Int): T

  fun valueWithUpdateConflict(existing: T, conflicting: T, sequence: Int): T
}
