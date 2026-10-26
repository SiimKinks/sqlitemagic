@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.SelectNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Common table expression, which can be used as a source in FROM and JOIN clauses.
 *
 * Declare it with [WITH].
 *
 * @param T Row type
 * @see [SQLite documentation: The WITH Clause](https://www.sqlite.org/lang_with.html)
 */
class Cte<T> internal constructor(
  override val occurrence: CteTable<T>
) : QueryOutputSource<T>() {
  /**
   * Create an alias for this common table expression.
   *
   * The alias names this reference in FROM and JOIN clauses, for example to join the common table expression with
   * itself. The common table expression keeps its name; use `(query AS "name").asCte()` to name it.
   *
   * @param alias The alias name
   * @return New common table expression reference with provided alias
   */
  @CheckResult
  infix fun AS(alias: String) = Cte(
    CteTable<T>(
      declaration = occurrence.declaration,
      alias = alias
    )
  )

  override fun cte() = this
}

internal class CteDeclaration(
  val node: SelectNode<*, *>,
  val name: String?
)

internal fun <T> declared(query: SelectQuery<T, *>, name: String?) = Cte(
  CteTable<T>(
    declaration = CteDeclaration(
      node = query.nestedNode(),
      name = name
    ),
    alias = null
  )
)

internal class CteTable<T>(
  val declaration: CteDeclaration,
  alias: String?
) : QueryOutputTable<T>(
  node = declaration.node,
  name = alias ?: declaration.name ?: "",
  alias = alias
) {
  override val sqlQualifier = (alias ?: declaration.name)?.let(::quoteSqlIdentifier) ?: ""

  override val label get() = alias ?: declaration.name

  override val kind get() = "CTE"

  override fun qualifier(context: SqlRenderingContext) = when (alias) {
    null -> context.cteName(declaration)
    else -> sqlQualifier
  }

  override fun renderSource(context: SqlRenderingContext) {
    context.sql.append(context.cteName(declaration))
    if (alias != null) {
      context.sql
        .append(" AS ")
        .append(sqlQualifier)
    }
  }

  override fun renderOutputs(context: SqlRenderingContext) = context.appendCteOutputs(
    occurrence = this,
    declaration = declaration
  )

  @Suppress("UNCHECKED_CAST")
  override fun reader(context: SqlRenderingContext) = context.cteReader(declaration) as ExecutionReader<T>
}
