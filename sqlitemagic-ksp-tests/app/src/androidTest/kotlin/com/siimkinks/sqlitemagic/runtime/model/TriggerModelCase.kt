package com.siimkinks.sqlitemagic.runtime.model

interface TriggerModelCase<T> :
  StandardBulkPersistModelCase<T>,
  StandardBulkDeleteModelCase<T>

interface RecursiveTriggerModelCase<T> :
  StandardBulkPersistModelCase<T>,
  StandardBulkDeleteModelCase<T>,
  RecursiveModelCase<T>

interface TriggerConflictModelCase<T> :
  PersistConflictModelCase<T>,
  BulkUpdateConflictModelCase<T>

interface RecursiveTriggerConflictModelCase<T> : RecursivePersistUpdateConflictModelCase<T>
