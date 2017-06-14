package com.siimkinks.sqlitemagic.model.immutable

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.model.TransformableObject

@Table(persistAll = true, useAccessMethods = true)
internal data class ImmutableValue(
    @Id val id: Long?,
    val stringValue: String,
    val aBoolean: Boolean,
    val integer: Int,
    val transformableObject: TransformableObject
)