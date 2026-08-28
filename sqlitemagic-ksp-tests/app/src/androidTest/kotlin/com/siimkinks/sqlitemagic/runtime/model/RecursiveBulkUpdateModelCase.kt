package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveBulkUpdateModelCase<T> : BulkUpdateModelCase<T>, RecursiveBulkInsertModelCase<T>
