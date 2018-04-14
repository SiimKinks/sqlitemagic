package com.siimkinks.sqlitemagic.another

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import java.util.*

@Table
data class ModelWithExternalTransformers(
    @Id val localId: Long?,
    val date: Date
)