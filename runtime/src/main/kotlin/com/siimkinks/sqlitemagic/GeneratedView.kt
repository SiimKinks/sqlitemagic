package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * A generated view and its lazily compiled defining query.
 *
 * Definition provider initialization dependencies must be acyclic. Concurrent access may evaluate the provider more
 * than once; the first successfully published definition is retained.
 */
class GeneratedView(
  val viewName: String,
  val temporary: Boolean,
  definitionProvider: () -> ViewDefinition
) {
  internal val identity = SqliteSchemaIdentity.from(
    schema = when {
      temporary -> TEMPORARY
      else -> MAIN
    },
    rawName = viewName
  )

  val definition by lazy(
    mode = PUBLICATION,
    initializer = definitionProvider
  )
}
