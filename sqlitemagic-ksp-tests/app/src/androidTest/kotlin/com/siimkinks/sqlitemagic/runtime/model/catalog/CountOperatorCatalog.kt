package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.runtime.model.CountOperator.*
import com.siimkinks.sqlitemagic.runtime.model.CountOperatorCase

internal object CountOperatorCatalog {
  val cases = listOf(
    CountOperatorCase(
      label = "isZero",
      operator = ZERO,
      emptyValue = true,
      nonEmptyValue = false
    ),
    CountOperatorCase(
      label = "isNotZero",
      operator = NOT_ZERO,
      emptyValue = false,
      nonEmptyValue = true
    )
  )
}
