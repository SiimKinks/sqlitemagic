package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table("order")
data class SqlKeywordOrder(
  @Id var id: Long? = null,
  var group: String? = null,
  var index: Boolean? = null,
  var default: Boolean = false
)

@Table("where")
class SqlKeywordWhere {
  @Id
  var id: Long? = null
  var values: String? = null
  @Column(onDeleteCascade = true)
  var order: SqlKeywordOrder? = null
  var limit: Int? = null

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is SqlKeywordWhere -> false
    else -> id == other.id &&
        values == other.values &&
        order == other.order &&
        limit == other.limit
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + (values?.hashCode() ?: 0)
    result = 31 * result + (order?.hashCode() ?: 0)
    result = 31 * result + (limit ?: 0)
    return result
  }
}
