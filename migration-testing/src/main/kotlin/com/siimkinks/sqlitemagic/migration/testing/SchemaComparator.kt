package com.siimkinks.sqlitemagic.migration.testing

internal data class SchemaDifference(
  val path: String,
  val expected: String,
  val actual: String
) {
  override fun toString() = "$path\n  expected: $expected\n  actual:   $actual"
}

internal object SchemaComparator {
  fun compare(
    expected: InspectedSchema,
    actual: InspectedSchema
  ): List<SchemaDifference> = buildList {
    (expected.tables.keys + actual.tables.keys)
      .sorted()
      .forEach { name ->
        val left = expected.tables[name]
        val right = actual.tables[name]
        val path = "table[$name]"
        when {
          left == null || right == null -> addDifference(
            path = path,
            expected = left,
            actual = right
          )
          else -> {
            addDifference(
              path = "$path.kind",
              expected = left.kind,
              actual = right.kind
            )
            addDifference(
              path = "$path.withoutRowId",
              expected = left.withoutRowId,
              actual = right.withoutRowId
            )
            addDifference(
              path = "$path.strict",
              expected = left.strict,
              actual = right.strict
            )
            addDifference(
              path = "$path.rowIdAlias",
              expected = left.rowIdAlias,
              actual = right.rowIdAlias
            )
            addDifference(
              path = "$path.columnOrder",
              expected = left.columns.map(InspectedColumn::name),
              actual = right.columns.map(InspectedColumn::name)
            )
            (left.columns.map(InspectedColumn::name) + right.columns.map(InspectedColumn::name))
              .toSortedSet()
              .forEach { column ->
                addDifference(
                  path = "$path.column[$column]",
                  expected = left.columns.singleOrNull { it.name == column },
                  actual = right.columns.singleOrNull { it.name == column }
                )
              }
            addDifference(
              path = "$path.constraints",
              expected = left.clauses,
              actual = right.clauses
            )
            addDifference(
              path = "$path.foreignKeys",
              expected = left.foreignKeys,
              actual = right.foreignKeys
            )
            addDifference(
              path = "$path.indices",
              expected = left.indices,
              actual = right.indices
            )
          }
        }
      }
  }

  private fun MutableList<SchemaDifference>.addDifference(
    path: String,
    expected: Any?,
    actual: Any?
  ) {
    if (expected != actual) {
      add(
        SchemaDifference(
          path = path,
          expected = expected
            .toString()
            .take(2000),
          actual = actual
            .toString()
            .take(2000)
        )
      )
    }
  }
}
