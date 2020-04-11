package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import org.threeten.bp.Month

@Table
data class TestTable(
    @Id val id: Long?,
    val month: Month
)