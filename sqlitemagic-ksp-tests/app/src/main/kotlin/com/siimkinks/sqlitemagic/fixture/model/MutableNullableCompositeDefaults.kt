package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

class MutableNullableDetails {
  var label: String = "default-label"
  var count: Long = 42

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is MutableNullableDetails -> false
    else -> label == other.label && count == other.count
  }

  override fun hashCode(): Int = 31 * label.hashCode() + count.hashCode()
}

@Table
class MutableNullableOwner {
  @Id
  var id: Long = 1

  override fun equals(other: Any?) =
    other is MutableNullableOwner &&
        id == other.id

  override fun hashCode() = id.hashCode()
}

@Table
class MutableNullableCompositeDefaults {
  @Id
  var id: Long = 0

  @Embedded
  var details: MutableNullableDetails? = MutableNullableDetails()
  var owner: MutableNullableOwner? = MutableNullableOwner()

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is MutableNullableCompositeDefaults -> false
    else -> id == other.id &&
        details == other.details &&
        owner == other.owner
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + details.hashCode()
    result = 31 * result + owner.hashCode()
    return result
  }
}
