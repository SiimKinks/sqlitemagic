package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class TypedRowSelectContractTest {
  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "one named expression | val invalid = SELECT.COLUMNS(first = 1L.asValue()) | No parameter with name 'first'",
      "ten named expressions | val invalid = SELECT.COLUMNS(first = id, second = id, third = id, fourth = id, " +
          "fifth = id, sixth = id, seventh = id, eighth = id, ninth = id, tenth = id) | No parameter with name 'tenth'",
      "stage scalar reader | val invalid = pair.readAs(SqlType.STRING) | $SCALAR_READ_AS_RECEIVER_MISMATCH",
      "query scalar reader | val invalid = pair.usingConnection(connection).readAs(SqlType.STRING) | " +
          SCALAR_READ_AS_RECEIVER_MISMATCH,
      "fewer constructor parameters | val invalid = pair.mapTo(::Single) | " +
          "None of the following candidates is applicable",
      "more constructor parameters | val invalid = pair.mapTo(::Triple) | " +
          "None of the following candidates is applicable",
      "non-null parameter for a nullable output | val invalid = pair.mapTo(::Strict) | " +
          "None of the following candidates is applicable",
      "swapped parameter types | val invalid = pair.mapTo(::Swapped) | None of the following candidates is applicable",
      "entity query | val invalid = SELECT.FROM(table).mapTo(::Single) | " +
          "SelectQuery<Row2<A, B>, RowSelection<Row2<A, B>>>.mapTo(transform: (A, B) -> M): " +
          "SelectQuery<M, RowSelection<M>>' is inapplicable because of a receiver type mismatch"
    ]
  )
  fun `typed rows outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedTypedRow.kt",
        contents = """
          package downstream.typedrow

          import com.siimkinks.sqlitemagic.DbConnection
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.asValue
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.readAs

          data class Single(val id: Long)
          data class Triple(val id: Long, val name: String?, val extra: Int)
          data class Strict(val id: Long, val name: String)
          data class Swapped(val name: String?, val id: Long)

          fun verify(
            table: Table<Any>,
            connection: DbConnection
          ) {
            val id = 1L.asValue()
            val pair = SELECT
                .COLUMNS(id, (null as String?).asValue())
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }

  @Test
  fun `typed rows infer their row types through shared stages, terminals and mappings`() {
    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "TypedRowConsumer.kt",
          contents = """
            package downstream.typedrow.pair

            import com.siimkinks.sqlitemagic.CompiledCount
            import com.siimkinks.sqlitemagic.CompiledCursor
            import com.siimkinks.sqlitemagic.CompiledFirst
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.DbConnection
            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.Row2
            import com.siimkinks.sqlitemagic.RowSelection
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.SelectFromStage
            import com.siimkinks.sqlitemagic.SelectGroupStage
            import com.siimkinks.sqlitemagic.SelectHavingStage
            import com.siimkinks.sqlitemagic.SelectLimitStage
            import com.siimkinks.sqlitemagic.SelectOffsetStage
            import com.siimkinks.sqlitemagic.SelectOrderStage
            import com.siimkinks.sqlitemagic.SelectProjectionStage
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.SelectWhereStage
            import com.siimkinks.sqlitemagic.SqlExpression
            import com.siimkinks.sqlitemagic.SqlNullable
            import com.siimkinks.sqlitemagic.Table
            import com.siimkinks.sqlitemagic.asValue
            import typewitness.Exact
            import typewitness.exactType

            class Sentinel
            class NullableDecoded

            fun verify(
              table: Table<Any>,
              connection: DbConnection,
              required: SqlExpression<Long, Required, Long>,
              nullable: SqlExpression<String, SqlNullable, String?>,
              sentinel: SqlExpression<Sentinel, SqlNullable, Sentinel>,
              nullableDecoder: SqlExpression<NullableDecoded?, Required, NullableDecoded?>
            ) {
              val sourceFree = SELECT
                  .COLUMNS(7L.asValue(), (null as String?).asValue())
              val projection = SELECT
                  .COLUMNS(required, nullable)
              val projectionFirst = projection.FROM(table)
              val policies = SELECT
                  .COLUMNS(sentinel, nullableDecoder)
              val reversedPolicies = SELECT
                  .COLUMNS(nullableDecoder, sentinel)
                  .FROM(table)
              val distinct = SELECT
                  .DISTINCT(required, nullable)
              val attachedDistinct = distinct.FROM(table)
              val filtered = attachedDistinct
                  .WHERE(true.asValue())
              val grouped = filtered.GROUP_BY(required, nullable)
              val having = grouped.HAVING(true.asValue())
              val ordered = having.ORDER_BY(required.ASC())
              val limited = ordered.LIMIT(20)
              val offset = limited.OFFSET(1)
              val connected = offset.usingConnection(connection)
              val compiled = connected.compile()
              val first = connected.takeFirst()
              val count = connected.count()
              val cursor = connected.toCursor()
              val rows = connected.execute()

              val sourceFreeType = sourceFree.exactType()
              val projectionType = projection.exactType()
              val projectionFirstType = projectionFirst.exactType()
              val policiesType = policies.exactType()
              val reversedPoliciesType = reversedPolicies.exactType()
              val distinctType = distinct.exactType()
              val attachedDistinctType = attachedDistinct.exactType()
              val filteredType = filtered.exactType()
              val groupedType = grouped.exactType()
              val havingType = having.exactType()
              val orderedType = ordered.exactType()
              val limitedType = limited.exactType()
              val offsetType = offset.exactType()
              val connectedType = connected.exactType()
              val exactSourceFree:
                  Exact<SelectProjectionStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> = sourceFreeType
              val exactProjection:
                  Exact<SelectProjectionStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> = projectionType
              val exactProjectionFirst: Exact<SelectFromStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  projectionFirstType
              val exactPolicies:
                  Exact<SelectProjectionStage<
                      Row2<Sentinel, NullableDecoded?>,
                      RowSelection<Row2<Sentinel, NullableDecoded?>>
                  >> =
                  policiesType
              val exactReversedPolicies:
                  Exact<SelectFromStage<
                      Row2<NullableDecoded?, Sentinel>,
                      RowSelection<Row2<NullableDecoded?, Sentinel>>
                  >> =
                  reversedPoliciesType
              val exactDistinct: Exact<SelectProjectionStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  distinctType
              val exactAttachedDistinct:
                  Exact<SelectFromStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> = attachedDistinctType
              val exactFiltered: Exact<SelectWhereStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  filteredType
              val exactGrouped: Exact<SelectGroupStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  groupedType
              val exactHaving: Exact<SelectHavingStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  havingType
              val exactOrdered: Exact<SelectOrderStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  orderedType
              val exactLimited: Exact<SelectLimitStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  limitedType
              val exactOffset: Exact<SelectOffsetStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  offsetType
              val exactConnected: Exact<SelectQuery<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                  connectedType
              val compiledType = compiled.exactType()
              val exactCompiled: Exact<CompiledSelect<Row2<Long, String?>>> = compiledType
              val firstType = first.exactType()
              val exactFirst: Exact<CompiledFirst<Row2<Long, String?>>> = firstType
              val countType = count.exactType()
              val exactCount: Exact<CompiledCount> = countType
              val cursorType = cursor.exactType()
              val exactCursor: Exact<CompiledCursor<Row2<Long, String?>>> = cursorType
              val rowsType = rows.exactType()
              val exactRows: Exact<List<Row2<Long, String?>>> = rowsType
            }
          """
        ),
        SourceFile.kotlin(
          name = "WideTypedRowConsumer.kt",
          contents = """
            package downstream.typedrow.wide

            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.Row3
            import com.siimkinks.sqlitemagic.Row4
            import com.siimkinks.sqlitemagic.Row5
            import com.siimkinks.sqlitemagic.Row6
            import com.siimkinks.sqlitemagic.Row7
            import com.siimkinks.sqlitemagic.Row8
            import com.siimkinks.sqlitemagic.Row9
            import com.siimkinks.sqlitemagic.RowSelection
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.SelectFromStage
            import com.siimkinks.sqlitemagic.SelectProjectionStage
            import com.siimkinks.sqlitemagic.SqlExpression
            import com.siimkinks.sqlitemagic.SqlNullable
            import com.siimkinks.sqlitemagic.Table
            import typewitness.Exact
            import typewitness.exactType

            class Sentinel
            class NullableDecoded

            typealias R3 = Row3<Long, String?, Sentinel>
            typealias R4 = Row4<Long, String?, Sentinel, NullableDecoded?>
            typealias R5 = Row5<Long, String?, Sentinel, NullableDecoded?, Long>
            typealias R6 = Row6<Long, String?, Sentinel, NullableDecoded?, Long, String?>
            typealias R7 = Row7<Long, String?, Sentinel, NullableDecoded?, Long, String?, Sentinel>
            typealias R8 = Row8<Long, String?, Sentinel, NullableDecoded?, Long, String?, Sentinel, NullableDecoded?>
            typealias R9 =
                Row9<Long, String?, Sentinel, NullableDecoded?, Long, String?, Sentinel, NullableDecoded?, Long>

            fun verify(
              table: Table<Any>,
              required: SqlExpression<Long, Required, Long>,
              nullable: SqlExpression<String, SqlNullable, String?>,
              sentinel: SqlExpression<Sentinel, SqlNullable, Sentinel>,
              nullableDecoder: SqlExpression<NullableDecoded?, Required, NullableDecoded?>
            ) {
              val row3 = SELECT
                  .COLUMNS(required, nullable, sentinel)
                  .FROM(table)
              val distinct3 = SELECT
                  .DISTINCT(required, nullable, sentinel)
              val row3Type = row3.exactType()
              val exactRow3: Exact<SelectFromStage<R3, RowSelection<R3>>> = row3Type
              val distinct3Type = distinct3.exactType()
              val exactDistinct3: Exact<SelectProjectionStage<R3, RowSelection<R3>>> = distinct3Type
              val row4 = SELECT
                  .COLUMNS(required, nullable, sentinel, nullableDecoder)
                  .FROM(table)
              val distinct4 = SELECT
                  .DISTINCT(required, nullable, sentinel, nullableDecoder)
              val row4Type = row4.exactType()
              val exactRow4: Exact<SelectFromStage<R4, RowSelection<R4>>> = row4Type
              val distinct4Type = distinct4.exactType()
              val exactDistinct4: Exact<SelectProjectionStage<R4, RowSelection<R4>>> = distinct4Type
              val row5 = SELECT
                  .COLUMNS(required, nullable, sentinel, nullableDecoder, required)
                  .FROM(table)
              val distinct5 = SELECT
                  .DISTINCT(required, nullable, sentinel, nullableDecoder, required)
              val row5Type = row5.exactType()
              val exactRow5: Exact<SelectFromStage<R5, RowSelection<R5>>> = row5Type
              val distinct5Type = distinct5.exactType()
              val exactDistinct5: Exact<SelectProjectionStage<R5, RowSelection<R5>>> = distinct5Type
              val row6 = SELECT
                  .COLUMNS(required, nullable, sentinel, nullableDecoder, required, nullable)
                  .FROM(table)
              val distinct6 = SELECT
                  .DISTINCT(required, nullable, sentinel, nullableDecoder, required, nullable)
              val row6Type = row6.exactType()
              val exactRow6: Exact<SelectFromStage<R6, RowSelection<R6>>> = row6Type
              val distinct6Type = distinct6.exactType()
              val exactDistinct6: Exact<SelectProjectionStage<R6, RowSelection<R6>>> = distinct6Type
              val row7 = SELECT
                  .COLUMNS(required, nullable, sentinel, nullableDecoder, required, nullable, sentinel)
                  .FROM(table)
              val distinct7 = SELECT
                  .DISTINCT(required, nullable, sentinel, nullableDecoder, required, nullable, sentinel)
              val row7Type = row7.exactType()
              val exactRow7: Exact<SelectFromStage<R7, RowSelection<R7>>> = row7Type
              val distinct7Type = distinct7.exactType()
              val exactDistinct7: Exact<SelectProjectionStage<R7, RowSelection<R7>>> = distinct7Type
              val row8 = SELECT
                  .COLUMNS(required, nullable, sentinel, nullableDecoder, required, nullable, sentinel, nullableDecoder)
                  .FROM(table)
              val distinct8 = SELECT
                  .DISTINCT(
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder
                  )
              val row8Type = row8.exactType()
              val exactRow8: Exact<SelectFromStage<R8, RowSelection<R8>>> = row8Type
              val distinct8Type = distinct8.exactType()
              val exactDistinct8: Exact<SelectProjectionStage<R8, RowSelection<R8>>> = distinct8Type
              val row9 = SELECT
                  .COLUMNS(
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required
                  )
                  .FROM(table)
              val distinct9 = SELECT
                  .DISTINCT(
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required
                  )
              val row9Type = row9.exactType()
              val exactRow9: Exact<SelectFromStage<R9, RowSelection<R9>>> = row9Type
              val distinct9Type = distinct9.exactType()
              val exactDistinct9: Exact<SelectProjectionStage<R9, RowSelection<R9>>> = distinct9Type
              val (first, second, third, fourth, fifth, sixth, seventh, eighth, ninth) = R9(
                1L,
                null,
                Sentinel(),
                null,
                5L,
                "six",
                Sentinel(),
                NullableDecoded(),
                9L
              )
            }
          """
        ),
        SourceFile.kotlin(
          name = "MappedTypedRowConsumer.kt",
          contents = """
            package downstream.typedrow.mapped

            import com.siimkinks.sqlitemagic.CompiledCursor
            import com.siimkinks.sqlitemagic.CompiledFirst
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.DbConnection
            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.RowSelection
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.ScalarSelection
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.SqlExpression
            import com.siimkinks.sqlitemagic.SqlNullable
            import com.siimkinks.sqlitemagic.SqlType
            import com.siimkinks.sqlitemagic.Table
            import com.siimkinks.sqlitemagic.mapTo
            import com.siimkinks.sqlitemagic.readAs
            import typewitness.Exact
            import typewitness.exactType

            class Sentinel
            class NullableDecoded

            data class AuthorId(val value: Long)
            data class Policies(val sentinel: Sentinel, val decoded: NullableDecoded?, val name: String?)
            data class Wide(
              val first: Long,
              val second: String?,
              val third: Sentinel,
              val fourth: NullableDecoded?,
              val fifth: Long,
              val sixth: String?,
              val seventh: Sentinel,
              val eighth: NullableDecoded?,
              val ninth: Long
            )

            fun verify(
              table: Table<Any>,
              connection: DbConnection,
              required: SqlExpression<Long, Required, Long>,
              nullable: SqlExpression<String, SqlNullable, String?>,
              sentinel: SqlExpression<Sentinel, SqlNullable, Sentinel>,
              nullableDecoder: SqlExpression<NullableDecoded?, Required, NullableDecoded?>
            ) {
              val ids = SELECT
                  .COLUMN(required)
                  .FROM(table)
                  .ORDER_BY(required.ASC())
                  .mapTo(::AuthorId)
              val readIds = SELECT
                  .COLUMN(required)
                  .FROM(table)
                  .readAs(SqlType.LONG)
                  .mapTo(::AuthorId)
              val policies = SELECT
                  .COLUMNS(sentinel, nullableDecoder, nullable)
                  .FROM(table)
                  .mapTo(::Policies)
              val connectedFirst = SELECT
                  .COLUMNS(sentinel, nullableDecoder, nullable)
                  .FROM(table)
                  .usingConnection(connection)
                  .mapTo(::Policies)
              val wide = SELECT
                  .COLUMNS(
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required,
                    nullable,
                    sentinel,
                    nullableDecoder,
                    required
                  )
                  .FROM(table)
                  .mapTo(::Wide)
                  .usingConnection(connection)
              val lambda = SELECT
                  .COLUMNS(required, nullable)
                  .FROM(table)
                  .mapTo { id, name -> name ?: id.toString() }

              val idsType = ids.exactType()
              val exactIds: Exact<SelectQuery<AuthorId, ScalarSelection<Long, Required, AuthorId>>> = idsType
              val readIdsType = readIds.exactType()
              val exactReadIds: Exact<SelectQuery<AuthorId, ScalarSelection<Long, Required, AuthorId>>> =
                  readIdsType
              val policiesType = policies.exactType()
              val exactPolicies: Exact<SelectQuery<Policies, RowSelection<Policies>>> = policiesType
              val connectedFirstType = connectedFirst.exactType()
              val exactConnectedFirst: Exact<SelectQuery<Policies, RowSelection<Policies>>> =
                  connectedFirstType
              val wideType = wide.exactType()
              val exactWide: Exact<SelectQuery<Wide, RowSelection<Wide>>> = wideType
              val lambdaType = lambda.exactType()
              val exactLambda: Exact<SelectQuery<String, RowSelection<String>>> = lambdaType
              val compiledType = policies.compile().exactType()
              val exactCompiled: Exact<CompiledSelect<Policies>> = compiledType
              val firstType = wide.takeFirst().exactType()
              val exactFirst: Exact<CompiledFirst<Wide>> = firstType
              val cursorType = ids.toCursor().exactType()
              val exactCursor: Exact<CompiledCursor<AuthorId>> = cursorType
              val rowsType = policies.execute().exactType()
              val exactRows: Exact<List<Policies>> = rowsType
            }
          """
        )
      )
      .isOk()
  }

  private companion object {
    const val SCALAR_READ_AS_RECEIVER_MISMATCH = "SelectQuery<T, ScalarSelection<S, Required, T>>.readAs(" +
        "descriptor: ReadType<*, *, V>): SelectQuery<V, ScalarSelection<S, Required, V>>' " +
        "is inapplicable because of a receiver type mismatch"
  }
}
