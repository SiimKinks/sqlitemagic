package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table
data class ModelWithTransformers(
    @Id val localId: Long?,
    val date: Date
)