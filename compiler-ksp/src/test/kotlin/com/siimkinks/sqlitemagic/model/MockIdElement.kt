package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.model.AutoIncrementMode.AUTOMATIC

fun mockIdElement(
  autoIncrementMode: AutoIncrementMode = AUTOMATIC,
  isAutoIncrement: Boolean = true,
  canAssignGeneratedId: Boolean = true
) = IdElement(
  autoIncrementMode = autoIncrementMode,
  isAutoIncrement = isAutoIncrement,
  canAssignGeneratedId = canAssignGeneratedId
)
