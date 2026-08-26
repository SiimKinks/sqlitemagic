package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class EntityWithRelationship {
  @Id
  var id: Long? = null
  var value: String? = null
  @Column(onDeleteCascade = true)
  var relatedEntity: SimpleMutableEntity? = null
  var count: Int? = null

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is EntityWithRelationship -> false
    else -> id == other.id &&
        value == other.value &&
        relatedEntity == other.relatedEntity &&
        count == other.count
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + (value?.hashCode() ?: 0)
    result = 31 * result + (relatedEntity?.hashCode() ?: 0)
    result = 31 * result + (count ?: 0)
    return result
  }
}
