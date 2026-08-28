package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveBulkPersistModelCase<T> : BulkPersistModelCase<T>, RecursiveBulkUpdateModelCase<T>
