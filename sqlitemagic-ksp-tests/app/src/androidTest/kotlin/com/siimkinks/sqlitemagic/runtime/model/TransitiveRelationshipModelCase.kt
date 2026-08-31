package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

data class TransitiveRelationshipTableRows(
  val table: Table<*>,
  val rows: List<*>
)

interface TransitiveRelationshipModelCase<T> :
  RelationshipQueryModelCase<T>,
  RecursiveTriggerModelCase<T>,
  RecursiveBulkPersistModelCase<T> {
  fun transitiveRelatedTableRows(value: T): List<TransitiveRelationshipTableRows>

  fun withMissingRelationshipIdentity(value: T): T
}
