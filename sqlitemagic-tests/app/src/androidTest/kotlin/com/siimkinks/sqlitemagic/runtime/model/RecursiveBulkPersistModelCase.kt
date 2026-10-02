package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveBulkPersistModelCase<T> : StandardBulkPersistModelCase<T>, RecursiveBulkUpdateModelCase<T>
