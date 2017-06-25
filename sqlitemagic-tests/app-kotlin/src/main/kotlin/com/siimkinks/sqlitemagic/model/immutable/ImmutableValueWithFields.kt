package com.siimkinks.sqlitemagic.model.immutable

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.model.TransformableObject

@Table(persistAll = true)
data class ImmutableValueWithFields(
    @JvmField
    @Id val id: Long?,
    @JvmField
    val stringValue: String,
    @JvmField
    val aBoolean: Boolean,
    @JvmField
    val integer: Int,
    @JvmField
    val aDouble: Double,
    @JvmField
    val aShort: Short,
    @JvmField
    val transformableObject: TransformableObject
)