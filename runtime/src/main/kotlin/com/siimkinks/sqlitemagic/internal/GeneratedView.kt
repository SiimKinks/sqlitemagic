package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.sql.compileViewDefinition
import kotlin.LazyThreadSafetyMode.PUBLICATION

/** Definition provider initialization dependencies must be acyclic. */
@InternalSqliteMagicApi
class GeneratedView internal constructor(
  internal val viewName: String,
  internal val temporary: Boolean,
  definitionProvider: () -> ViewDefinition
) {
  /** [query] provides the defining query, whose result columns are named [columns] and then the [blocks]. */
  constructor(
    viewName: String,
    temporary: Boolean,
    query: () -> SelectQuery<*, *>,
    columns: List<String>,
    blocks: List<ViewBlock> = emptyList()
  ) : this(
    viewName = viewName,
    temporary = temporary,
    definitionProvider = {
      compileViewDefinition(
        query = query(),
        viewName = viewName,
        columns = columns,
        blocks = blocks
      )
    }
  )

  internal val identity = SqliteSchemaIdentity.from(
    schema = when {
      temporary -> TEMPORARY
      else -> MAIN
    },
    rawName = viewName
  )

  internal val definition by lazy(
    mode = PUBLICATION,
    initializer = definitionProvider
  )
}
