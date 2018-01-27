package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

const val IMMUTABLE_VALUE_INDEX = "immutable_value_index"

@Index(IMMUTABLE_VALUE_INDEX)
@Table(persistAll = true, useAccessMethods = true)
data class ImmutableValue(
    @Id val id: Long?,
    @Column(belongsToIndex = IMMUTABLE_VALUE_INDEX)
    val stringValue: String,
    val aBoolean: Boolean,
    @Column(belongsToIndex = IMMUTABLE_VALUE_INDEX)
    val integer: Int,
    val transformableObject: SubmoduleTransformableObject
) {
  companion object {
    fun newRandom(): ImmutableValue {
      val r = Random()
      return ImmutableValue(
          id = r.nextLong(),
          stringValue = Utils.randomTableName(),
          aBoolean = r.nextBoolean(),
          integer = r.nextInt(),
          transformableObject = SubmoduleTransformableObject(r.nextInt())
      )
    }
  }
}