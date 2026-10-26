package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.mapTo

typealias ProjectionColumns = List<Column<*, *, *, *, *, *, *>>

interface SuccessfulModelProjectionCase<T> : InsertModelCase<T> {
  val projectionColumns: ProjectionColumns

  fun expectedAfterProjection(value: T): T

  fun projection() = SELECT
    .COLUMNS(*projectionColumns.toTypedArray())
    .FROM(table)
    .mapTo(table)
}
