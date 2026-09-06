package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.BETWEEN
import com.siimkinks.sqlitemagic.GLOB
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.IN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
import com.siimkinks.sqlitemagic.LIKE
import com.siimkinks.sqlitemagic.NOT_BETWEEN
import com.siimkinks.sqlitemagic.NOT_GLOB
import com.siimkinks.sqlitemagic.NOT_IN
import com.siimkinks.sqlitemagic.NOT_LIKE
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.runtime.model.QueryPredicateCase

internal object QueryPredicateCatalog {
  val rows = NonNullQueryFixture.nonNullRows

  val cases = listOf(
    QueryPredicateCase(
      name = "IS",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN IS true },
      expectedValues = listOf("non-null-string-1", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "IS_NOT",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN IS_NOT false },
      expectedValues = listOf("non-null-string-1", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "LIKE",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE LIKE "%string-2" },
      expectedValues = listOf("non-null-string-2")
    ),
    QueryPredicateCase(
      name = "NOT_LIKE",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE NOT_LIKE "%string-2" },
      expectedValues = listOf("non-null-string-1", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "GLOB",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE GLOB "non-null-string-[13]" },
      expectedValues = listOf("non-null-string-1", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "NOT_GLOB",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE NOT_GLOB "non-null-string-[13]" },
      expectedValues = listOf("non-null-string-2")
    ),
    QueryPredicateCase(
      name = "IN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER IN listOf(101, 303) },
      expectedValues = listOf("non-null-string-1", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "NOT_IN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER NOT_IN listOf(101, 303) },
      expectedValues = listOf("non-null-string-2")
    ),
    QueryPredicateCase(
      name = "empty IN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER IN emptyList() },
      expectedValues = emptyList()
    ),
    QueryPredicateCase(
      name = "empty NOT_IN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER NOT_IN emptyList() },
      expectedValues = listOf("non-null-string-1", "non-null-string-2", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "GREATER_THAN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER GREATER_THAN 150 },
      expectedValues = listOf("non-null-string-2", "non-null-string-3")
    ),
    QueryPredicateCase(
      name = "LESS_OR_EQUAL",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER LESS_OR_EQUAL 202 },
      expectedValues = listOf("non-null-string-1", "non-null-string-2")
    ),
    QueryPredicateCase(
      name = "BETWEEN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER BETWEEN (101 AND 202) },
      expectedValues = listOf("non-null-string-1", "non-null-string-2")
    ),
    QueryPredicateCase(
      name = "NOT_BETWEEN",
      predicate = { IMMUTABLE_VALUE_WITH_FIELDS.INTEGER NOT_BETWEEN (101 AND 202) },
      expectedValues = listOf("non-null-string-3")
    ),
    QueryPredicateCase(
      name = "AND",
      predicate = {
        (IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN IS true) AND
            (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER GREATER_THAN 150)
      },
      expectedValues = listOf("non-null-string-3")
    ),
    QueryPredicateCase(
      name = "OR",
      predicate = {
        (IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN IS false) OR
            (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER LESS_OR_EQUAL 101)
      },
      expectedValues = listOf("non-null-string-1", "non-null-string-2")
    )
  )

  fun seed() = NonNullQueryFixture.seed()
}
