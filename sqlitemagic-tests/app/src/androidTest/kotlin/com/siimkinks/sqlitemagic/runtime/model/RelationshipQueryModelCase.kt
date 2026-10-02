package com.siimkinks.sqlitemagic.runtime.model

interface RelationshipQueryModelCase<T> : InsertModelCase<T>, RecursiveModelCase<T> {
  fun expectedAfterShallowQuery(deepExpected: T): T
}
