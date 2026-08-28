package com.siimkinks.sqlitemagic.runtime.model

interface RecursivePersistConflictModelCase<T> :
  PersistConflictModelCase<T>,
  RecursiveInsertConflictModelCase<T>
