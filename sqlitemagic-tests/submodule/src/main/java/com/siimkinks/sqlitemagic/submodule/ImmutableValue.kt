package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.Utils
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table(persistAll = true, useAccessMethods = true)
data class ImmutableValue(
    @Id val id: Long?,
    val stringValue: String,
    val aBoolean: Boolean,
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