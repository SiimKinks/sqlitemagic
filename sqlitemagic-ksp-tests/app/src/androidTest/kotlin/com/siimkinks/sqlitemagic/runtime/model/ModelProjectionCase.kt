package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Column

typealias ProjectionColumns = List<Column<*, *, *, *, *>>

interface SuccessfulModelProjectionCase<T> : InsertModelCase<T> {
  val projectionColumns: ProjectionColumns

  fun expectedAfterProjection(value: T): T
}

interface MissingRequiredProjectionCase<T> : InsertModelCase<T> {
  val missingRequiredProjectionColumns: ProjectionColumns
  val expectedSQLExceptionMessage: String
}
