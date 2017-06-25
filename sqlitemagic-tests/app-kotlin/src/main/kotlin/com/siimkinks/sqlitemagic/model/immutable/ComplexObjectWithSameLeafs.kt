package com.siimkinks.sqlitemagic.model.immutable

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.model.Magazine

@Table(persistAll = true, useAccessMethods = true)
data class ComplexObjectWithSameLeafs(
    @Id val id: Long,
    val name: String,
    val simpleValue: ImmutableValueWithFields,
    val magazine: Magazine,
    val simpleValueDuplicate: ImmutableValueWithFields)