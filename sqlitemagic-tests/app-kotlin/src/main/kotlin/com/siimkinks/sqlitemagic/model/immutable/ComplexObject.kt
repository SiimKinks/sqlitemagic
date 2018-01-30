package com.siimkinks.sqlitemagic.model.immutable

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table(persistAll = true, useAccessMethods = true)
data class ComplexObject(
    @Id val id: Long? = null,
    val number: String,
    val childObject: ChildObject? = null
)

@Table(persistAll = true, useAccessMethods = true)
data class ComplexObjectWithUniqueColumn(
    @Id val id: Long? = null,
    @Unique val externalId: String,
    val number: String,
    val childObject: ChildObject? = null
)

@Table(persistAll = true, useAccessMethods = true)
data class ChildObject(
    @Id val id: Long? = null,
    val address: String? = null,
    val comment: String? = null
)