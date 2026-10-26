@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

/**
 * Create a query with a common table expression.
 *
 * SQL: WITH cte AS (SELECT...) SELECT...
 *
 * Name the common table expression with `query AS "name"`, otherwise it is named automatically.
 *
 * Example:
 * ```
 * val selectedId = AUTHOR.ID AS "author_id"
 * val authorIds = SELECT
 *     .COLUMN(selectedId)
 *     .FROM(AUTHOR)
 * WITH(authorIds) { ids ->
 *   SELECT
 *       .COLUMN(ids[selectedId])
 *       .FROM(ids)
 * }
 * ```
 *
 * @param query Definition of the common table expression
 * @param main Function that creates the main query with the common table expression
 * @return SQL SELECT statement
 * @see [SQLite documentation: The WITH Clause](https://www.sqlite.org/lang_with.html)
 */
fun <A, T, S : Selection<T>> WITH(
  query: QueryOutputSource<A>,
  main: (Cte<A>) -> SelectQuery<T, S>
) = query
  .cte()
  .let { first ->
    main(first).declaring(listOf(first))
  }

/**
 * Create a query with two common table expressions.
 *
 * SQL: WITH cte1 AS (SELECT...), cte2 AS (SELECT...) SELECT...
 *
 * Name a common table expression with `query AS "name"`, otherwise it is named automatically.
 *
 * @param first Definition of the first common table expression
 * @param second Definition of the second common table expression
 * @param main Function that creates the main query with the common table expressions
 * @return SQL SELECT statement
 * @see [SQLite documentation: The WITH Clause](https://www.sqlite.org/lang_with.html)
 */
fun <A, B, T, S : Selection<T>> WITH(
  first: QueryOutputSource<A>,
  second: QueryOutputSource<B>,
  main: (Cte<A>, Cte<B>) -> SelectQuery<T, S>
) = first.cte().let { firstCte ->
  second.cte().let { secondCte ->
    main(firstCte, secondCte).declaring(listOf(firstCte, secondCte))
  }
}

/**
 * Create a query with three common table expressions.
 *
 * SQL: WITH cte1 AS (SELECT...), cte2 AS (SELECT...), cte3 AS (SELECT...) SELECT...
 *
 * Name a common table expression with `query AS "name"`, otherwise it is named automatically.
 *
 * @param first Definition of the first common table expression
 * @param second Definition of the second common table expression
 * @param third Definition of the third common table expression
 * @param main Function that creates the main query with the common table expressions
 * @return SQL SELECT statement
 * @see [SQLite documentation: The WITH Clause](https://www.sqlite.org/lang_with.html)
 */
fun <A, B, C, T, S : Selection<T>> WITH(
  first: QueryOutputSource<A>,
  second: QueryOutputSource<B>,
  third: QueryOutputSource<C>,
  main: (Cte<A>, Cte<B>, Cte<C>) -> SelectQuery<T, S>
) = first.cte().let { firstCte ->
  second.cte().let { secondCte ->
    third.cte().let { thirdCte ->
      main(firstCte, secondCte, thirdCte).declaring(listOf(firstCte, secondCte, thirdCte))
    }
  }
}

/**
 * Create a query with common table expressions.
 *
 * Create the common table expressions with `asCte()` and use them in [main]. They are declared in the given order,
 * so a common table expression can use the ones before it.
 *
 * Example:
 * ```
 * val ids = SELECT
 *     .COLUMN(AUTHOR.ID)
 *     .FROM(AUTHOR)
 *     .asCte()
 * val titles = SELECT
 *     .COLUMN(BOOK.TITLE)
 *     .FROM(BOOK)
 *     .asCte()
 * WITH(ids, titles) {
 *   SELECT FROM titles
 * }
 * ```
 *
 * @param declarations Common table expressions to declare
 * @param main Function that creates the main query
 * @return SQL SELECT statement
 * @see [SQLite documentation: The WITH Clause](https://www.sqlite.org/lang_with.html)
 */
fun <T, S : Selection<T>> WITH(
  vararg declarations: Cte<*>,
  main: () -> SelectQuery<T, S>
) = main().declaring(declarations.asList())

private fun <T, S : Selection<T>> SelectQuery<T, S>.declaring(ctes: List<Cte<*>>) = SelectQuery(
  node.copy(with = ctes.map { it.occurrence.declaration } + node.with)
)
