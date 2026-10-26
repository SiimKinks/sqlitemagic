package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult

/**
 * Map each row of columns with [transform].
 *
 * Example:
 * ```
 * SELECT
 *     .RAW("SELECT id, name FROM author")
 *     .readAs(SqlType.LONG, SqlType.STRING.nullable)
 *     .mapTo(::AuthorName)
 * ```
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, M, O : RawTables> RawSelectQuery<Row2<A, B>, O>.mapTo(
  transform: (A, B) -> M
) = mapped(columns.read2(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, M, O : RawTables> RawSelectQuery<Row3<A, B, C>, O>.mapTo(
  transform: (A, B, C) -> M
) = mapped(columns.read3(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, M, O : RawTables> RawSelectQuery<Row4<A, B, C, D>, O>.mapTo(
  transform: (A, B, C, D) -> M
) = mapped(columns.read4(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, E, M, O : RawTables> RawSelectQuery<Row5<A, B, C, D, E>, O>.mapTo(
  transform: (A, B, C, D, E) -> M
) = mapped(columns.read5(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, E, F, M, O : RawTables> RawSelectQuery<Row6<A, B, C, D, E, F>, O>.mapTo(
  transform: (A, B, C, D, E, F) -> M
) = mapped(columns.read6(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, E, F, G, M, O : RawTables> RawSelectQuery<Row7<A, B, C, D, E, F, G>, O>.mapTo(
  transform: (A, B, C, D, E, F, G) -> M
) = mapped(columns.read7(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, E, F, G, H, M, O : RawTables> RawSelectQuery<Row8<A, B, C, D, E, F, G, H>, O>.mapTo(
  transform: (A, B, C, D, E, F, G, H) -> M
) = mapped(columns.read8(transform))

/**
 * Map each row of columns with [transform].
 *
 * @param transform Function that creates a result from the columns
 * @return Raw SQL SELECT statement
 */
@CheckResult
fun <A, B, C, D, E, F, G, H, I, M, O : RawTables> RawSelectQuery<Row9<A, B, C, D, E, F, G, H, I>, O>.mapTo(
  transform: (A, B, C, D, E, F, G, H, I) -> M
) = mapped(columns.read9(transform))
