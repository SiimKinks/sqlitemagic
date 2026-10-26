package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ScalarSelectContractTest {
  @Test
  fun `scalar projections, entity reads, source-free queries and SQL-order clause sequences keep their types`() {
    compile(
      GROUPED_CONSUMER,
      ORDERED_CONSUMER,
      DISTINCT_CONSUMER,
      ENTITY_FROM_CONSUMER,
      SOURCE_FREE_CONSUMER,
      CLAUSE_SEQUENCE_CONSUMER
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "source-first COLUMN | SELECT.FROM(table).COLUMN(1L.asValue()) | " +
          "Unresolved reference 'COLUMN' on receiver of type 'SelectFromStage<",
      "source-first COLUMNS | SELECT.FROM(table).COLUMNS(1L.asValue(), 2L.asValue()) | " +
          "Unresolved reference 'COLUMNS' on receiver of type 'SelectFromStage<",
      "DISTINCT after a projection | SELECT.COLUMN(1L.asValue()).DISTINCT() | " +
          "Unresolved reference 'DISTINCT' on receiver of type 'SelectProjectionStage<",
      "DISTINCT after an attached FROM | SELECT.COLUMN(1L.asValue()).FROM(table).DISTINCT() | " +
          "Unresolved reference 'DISTINCT' on receiver of type 'SelectFromStage<",
      "entity DISTINCT without the dot | SELECT DISTINCT FROM table | Unresolved reference 'FROM'",
      "entity readAs | SELECT.FROM(table).readAs(SqlType.LONG) | " +
          "SelectQuery<T, ScalarSelection<S, Required, T>>.readAs(descriptor: ReadType<*, *, V>): " +
          "SelectQuery<V, ScalarSelection<S, Required, V>>' is inapplicable because of a receiver type mismatch",
      "source already attached | SELECT.FROM(table).FROM(other) | " +
          "Unresolved reference 'FROM' on receiver of type 'SelectFromStage<",
      "FROM after WHERE | SELECT.COLUMN(1L.asValue()).WHERE(predicate).FROM(table) | " +
          "Unresolved reference 'FROM' on receiver of type 'SelectWhereStage<",
      "read only descriptors cannot encode | 1L.asValue(SqlType.NUMBER) | " +
          "actual type is 'ReadType<Number, Required, Number>', but 'EncodingType<",
      "WHERE cannot follow LIMIT | SELECT.COLUMN(1L.asValue()).LIMIT(1).WHERE(true.asValue()) | " +
          "Unresolved reference 'WHERE' on receiver of type 'SelectLimitStage<",
      "DISTINCT cannot follow WHERE | SELECT.COLUMN(1L.asValue()).WHERE(true.asValue()).DISTINCT() | " +
          "Unresolved reference 'DISTINCT' on receiver of type 'SelectWhereStage<",
      "DISTINCT cannot follow LIMIT | SELECT.COLUMN(1L.asValue()).LIMIT(1).DISTINCT() | " +
          "Unresolved reference 'DISTINCT' on receiver of type 'SelectLimitStage<",
      "DISTINCT cannot follow OFFSET | SELECT.COLUMN(1L.asValue()).LIMIT(1).OFFSET(0).DISTINCT() | " +
          "Unresolved reference 'DISTINCT' on receiver of type 'SelectOffsetStage<",
      "OFFSET requires LIMIT | SELECT.COLUMN(1L.asValue()).OFFSET(0) | " +
          "Unresolved reference 'OFFSET' on receiver of type 'SelectProjectionStage<",
      "WHERE cannot repeat | SELECT.COLUMN(1L.asValue()).WHERE(true.asValue()).WHERE(true.asValue()) | " +
          "Unresolved reference 'WHERE' on receiver of type 'SelectWhereStage<",
      "first is already compiled | SELECT.COLUMN(1L.asValue()).takeFirst().compile() | " +
          "Unresolved reference 'compile' on receiver of type 'CompiledFirst<",
      "clause after connection selection | " +
          "SELECT.COLUMN(1L.asValue()).usingConnection(connection).WHERE(true.asValue()) | " +
          "Unresolved reference 'WHERE' on receiver of type 'SelectQuery<",
      "ORDER BY after LIMIT | SELECT.COLUMN(1L.asValue()).LIMIT(1).ORDER_BY(1L.asValue().ASC()) | " +
          "Unresolved reference 'ORDER_BY' on receiver of type 'SelectLimitStage<",
      "ORDER BY after OFFSET | SELECT.COLUMN(1L.asValue()).LIMIT(1).OFFSET(0).ORDER_BY(1L.asValue().ASC()) | " +
          "Unresolved reference 'ORDER_BY' on receiver of type 'SelectOffsetStage<",
      "repeated ORDER BY | " +
          "SELECT.COLUMN(1L.asValue()).ORDER_BY(1L.asValue().ASC()).ORDER_BY(2L.asValue().ASC()) | " +
          "Unresolved reference 'ORDER_BY' on receiver of type 'SelectOrderStage<",
      "zero ordering terms | SELECT.COLUMN(1L.asValue()).ORDER_BY() | No value passed for parameter 'ordering'",
      "zero grouping terms | projection.GROUP_BY() | No value passed for parameter 'expression'",
      "HAVING without GROUP BY | projection.HAVING(true.asValue()) | " +
          "Unresolved reference 'HAVING' on receiver of type 'SelectProjectionStage<",
      "non Boolean HAVING | grouped.HAVING(1L.asValue()) | " +
          "actual type is 'EncodedSqlExpression<Long, Long, Required, Long>', " +
          "but 'SqlExpression<Boolean, *, *>' was expected",
      "repeated GROUP BY | grouped.GROUP_BY(1L.asValue()) | " +
          "Unresolved reference 'GROUP_BY' on receiver of type 'SelectGroupStage<",
      "repeated HAVING | having.HAVING(true.asValue()) | " +
          "Unresolved reference 'HAVING' on receiver of type 'SelectHavingStage<",
      "GROUP BY after ORDER BY | grouped.ORDER_BY(1L.asValue().ASC()).GROUP_BY(1L.asValue()) | " +
          "Unresolved reference 'GROUP_BY' on receiver of type 'SelectOrderStage<",
      "HAVING after ORDER BY | grouped.ORDER_BY(1L.asValue().ASC()).HAVING(true.asValue()) | " +
          "Unresolved reference 'HAVING' on receiver of type 'SelectOrderStage<",
      "GROUP BY after LIMIT | grouped.LIMIT(1).GROUP_BY(1L.asValue()) | " +
          "Unresolved reference 'GROUP_BY' on receiver of type 'SelectLimitStage<",
      "HAVING after LIMIT | grouped.LIMIT(1).HAVING(true.asValue()) | " +
          "Unresolved reference 'HAVING' on receiver of type 'SelectLimitStage<",
      "GROUP BY after OFFSET | grouped.LIMIT(1).OFFSET(0).GROUP_BY(1L.asValue()) | " +
          "Unresolved reference 'GROUP_BY' on receiver of type 'SelectOffsetStage<",
      "HAVING after OFFSET | grouped.LIMIT(1).OFFSET(0).HAVING(true.asValue()) | " +
          "Unresolved reference 'HAVING' on receiver of type 'SelectOffsetStage<"
    ]
  )
  fun `scalar queries outside their contracts do not compile at the owning stage`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compile(
      """
        fun invalid(
          table: Table<Any>,
          other: Table<Any>,
          predicate: SqlExpression<Boolean, *, *>,
          connection: DbConnection
        ) {
          val projection = SELECT.COLUMN(1L.asValue())
          val grouped = projection.GROUP_BY(1L.asValue())
          val having = grouped.HAVING(true.asValue())
          val invalid = $program
        }
      """
    ).assertCompilationError(diagnostic)
  }

  private fun compile(@Language("kotlin") vararg bodies: String) = PlainKotlinCompilation.compile(
    *bodies
      .mapIndexed { index, body ->
        SourceFile.kotlin(
          name = "ScalarConsumer$index.kt",
          contents = """
        package downstream.scalar$index

        import com.siimkinks.sqlitemagic.CompiledCount
        import com.siimkinks.sqlitemagic.CompiledFirst
        import com.siimkinks.sqlitemagic.CompiledSelect
        import com.siimkinks.sqlitemagic.DbConnection
        import com.siimkinks.sqlitemagic.EntitySelection
        import com.siimkinks.sqlitemagic.NULL
        import com.siimkinks.sqlitemagic.Required
        import com.siimkinks.sqlitemagic.Row2
        import com.siimkinks.sqlitemagic.RowSelection
        import com.siimkinks.sqlitemagic.SELECT
        import com.siimkinks.sqlitemagic.ScalarSelection
        import com.siimkinks.sqlitemagic.Select
        import com.siimkinks.sqlitemagic.SelectFromStage
        import com.siimkinks.sqlitemagic.SelectLimitStage
        import com.siimkinks.sqlitemagic.SelectOffsetStage
        import com.siimkinks.sqlitemagic.SelectPlan
        import com.siimkinks.sqlitemagic.SelectProjectionStage
        import com.siimkinks.sqlitemagic.SelectQuery
        import com.siimkinks.sqlitemagic.SelectWhereStage
        import com.siimkinks.sqlitemagic.Selection
        import com.siimkinks.sqlitemagic.SqlExpression
        import com.siimkinks.sqlitemagic.SqlNullability
        import com.siimkinks.sqlitemagic.SqlNullable
        import com.siimkinks.sqlitemagic.SqlType
        import com.siimkinks.sqlitemagic.Table
        import com.siimkinks.sqlitemagic.asLiteral
        import com.siimkinks.sqlitemagic.asValue
        import com.siimkinks.sqlitemagic.readAs
        import typewitness.Exact
        import typewitness.exactType

        $body
      """
        )
      }
      .toTypedArray()
  )

  private companion object {
    const val GROUPED_CONSUMER = """
        import com.siimkinks.sqlitemagic.SelectGroupStage
        import com.siimkinks.sqlitemagic.SelectHavingStage
        import com.siimkinks.sqlitemagic.SelectOrderStage

        fun verify(table: Table<Any>) {
          val sourceFree = SELECT.COLUMN(7L.asValue())
              .GROUP_BY(7L.asValue())
          val projectionFirst = SELECT.COLUMN(NULL)
              .FROM(table)
              .GROUP_BY(7L.asValue(), 8L.asValue())
          val distinctFromWhere = SELECT.DISTINCT(11L.asValue())
              .FROM(table)
              .WHERE(true.asValue())
              .GROUP_BY(11L.asValue())
          val entity = SELECT.FROM(table)
              .GROUP_BY(9L.asValue())
          val infix = (SELECT.COLUMN(10L.asValue()) GROUP_BY 10L.asValue())
          val having = distinctFromWhere.HAVING(true.asValue())
          val ordered = having.ORDER_BY(11L.asValue().ASC())
          val sourceFreeType = sourceFree.exactType()
          val projectionFirstType = projectionFirst.exactType()
          val distinctFromWhereType = distinctFromWhere.exactType()
          val entityType = entity.exactType()
          val infixType = infix.exactType()
          val havingType = having.exactType()
          val orderedType = ordered.exactType()
          val exactSourceFree: Exact<SelectGroupStage<Long, ScalarSelection<Long, Required, Long>>> = sourceFreeType
          val exactProjectionFirst:
              Exact<SelectGroupStage<Nothing?, ScalarSelection<Nothing?, SqlNullable, Nothing?>>> = projectionFirstType
          val exactDistinctFromWhere:
              Exact<SelectGroupStage<Long, ScalarSelection<Long, Required, Long>>> = distinctFromWhereType
          val exactEntity: Exact<SelectGroupStage<Any, EntitySelection<Any>>> = entityType
          val exactInfix: Exact<SelectGroupStage<Long, ScalarSelection<Long, Required, Long>>> = infixType
          val exactHaving: Exact<SelectHavingStage<Long, ScalarSelection<Long, Required, Long>>> = havingType
          val exactOrdered: Exact<SelectOrderStage<Long, ScalarSelection<Long, Required, Long>>> = orderedType
        }
    """

    const val ORDERED_CONSUMER = """
        import com.siimkinks.sqlitemagic.CompiledCursor
        import com.siimkinks.sqlitemagic.SelectOrderStage

        fun verify(table: Table<Any>, connection: DbConnection) {
          val sourceFree = SELECT.COLUMN(7L.asValue())
              .ORDER_BY(7L.asValue().ASC())
          val projectionFirst = SELECT.COLUMN(NULL)
              .FROM(table)
              .ORDER_BY(7L.asValue().ASC(), 8L.asValue().DESC())
          val distinctFromWhere = SELECT.DISTINCT(11L.asValue())
              .FROM(table)
              .WHERE(true.asValue())
              .ORDER_BY(11L.asValue().ASC())
          val entity = SELECT.FROM(table)
              .ORDER_BY(9L.asValue().DESC())
          val infix = (SELECT.COLUMN(10L.asValue()) ORDER_BY 10L.asValue().ASC())
          val limited = distinctFromWhere.LIMIT(2)
          val offset = limited.OFFSET(1)
          val decoded = offset.readAs(SqlType.STRING)
          val connected = decoded.usingConnection(connection)
          val compiled = connected.compile()
          val first = connected.takeFirst()
          val count = connected.count()
          val cursor = connected.toCursor()
          val sourceFreeType = sourceFree.exactType()
          val projectionFirstType = projectionFirst.exactType()
          val distinctFromWhereType = distinctFromWhere.exactType()
          val entityType = entity.exactType()
          val infixType = infix.exactType()
          val decodedType = decoded.exactType()
          val connectedType = connected.exactType()
          val limitedType = limited.exactType()
          val offsetType = offset.exactType()
          val compiledType = compiled.exactType()
          val firstType = first.exactType()
          val cursorType = cursor.exactType()
          val exactSourceFree: Exact<SelectOrderStage<Long, ScalarSelection<Long, Required, Long>>> = sourceFreeType
          val exactProjectionFirst:
              Exact<SelectOrderStage<Nothing?, ScalarSelection<Nothing?, SqlNullable, Nothing?>>> = projectionFirstType
          val exactDistinctFromWhere:
              Exact<SelectOrderStage<Long, ScalarSelection<Long, Required, Long>>> = distinctFromWhereType
          val exactEntity: Exact<SelectOrderStage<Any, EntitySelection<Any>>> = entityType
          val exactInfix: Exact<SelectOrderStage<Long, ScalarSelection<Long, Required, Long>>> = infixType
          val exactDecoded: Exact<SelectQuery<String, ScalarSelection<Long, Required, String>>> = decodedType
          val exactConnected: Exact<SelectQuery<String, ScalarSelection<Long, Required, String>>> = connectedType
          val exactLimited: Exact<SelectLimitStage<Long, ScalarSelection<Long, Required, Long>>> = limitedType
          val exactOffset: Exact<SelectOffsetStage<Long, ScalarSelection<Long, Required, Long>>> = offsetType
          val exactCompiled: Exact<CompiledSelect<String>> = compiledType
          val exactFirst: Exact<CompiledFirst<String>> = firstType
          val exactCount: CompiledCount = count
          val exactCursor: Exact<CompiledCursor<String>> = cursorType
        }
    """

    const val DISTINCT_CONSUMER = """
        fun verify(table: Table<Any>) {
          val infix = SELECT DISTINCT 7L.asValue()
          val fluent = SELECT.DISTINCT(7L.asValue())
              .FROM(table)
          val rows = SELECT.DISTINCT(7L.asValue(), "seven".asValue())
          val entities = SELECT.DISTINCT FROM table
          val fluentEntities = SELECT.DISTINCT.FROM(table)
          val infixType = infix.exactType()
          val fluentType = fluent.exactType()
          val rowsType = rows.exactType()
          val entitiesType = entities.exactType()
          val fluentEntitiesType = fluentEntities.exactType()
          val exactInfix: Exact<SelectProjectionStage<Long, ScalarSelection<Long, Required, Long>>> = infixType
          val exactFluent: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = fluentType
          val exactRows: Exact<SelectProjectionStage<Row2<Long, String>, RowSelection<Row2<Long, String>>>> = rowsType
          val exactEntities: Exact<SelectFromStage<Any, EntitySelection<Any>>> = entitiesType
          val exactFluentEntities: Exact<SelectFromStage<Any, EntitySelection<Any>>> = fluentEntitiesType
        }
    """

    const val ENTITY_FROM_CONSUMER = """
        import com.siimkinks.sqlitemagic.CompiledCursor
        import com.siimkinks.sqlitemagic.SelectOrderStage

        fun <T> captureRows(table: Table<T>, connection: DbConnection) = SELECT.FROM(table)
            .usingConnection(connection)
            .execute()

        fun verify(table: Table<Any>, connection: DbConnection) {
          val source = SELECT.FROM(table)
          val infix = (SELECT FROM table) WHERE true.asValue()
          val filtered = source.WHERE(true.asValue())
          val ordered = source.ORDER_BY(1L.asValue().ASC())
          val paged = source.LIMIT(2)
              .OFFSET(1)
          val connected = source.usingConnection(connection)
          val rows = connected.execute()
          val first = connected.takeFirst()
          val count = connected.count()
          val cursor = connected.toCursor()
          val captured = captureRows(
            table = table,
            connection = connection
          )
          val sourceType = source.exactType()
          val infixType = infix.exactType()
          val filteredType = filtered.exactType()
          val orderedType = ordered.exactType()
          val pagedType = paged.exactType()
          val rowsType = rows.exactType()
          val firstType = first.exactType()
          val cursorType = cursor.exactType()
          val capturedType = captured.exactType()
          val exactSource: Exact<SelectFromStage<Any, EntitySelection<Any>>> = sourceType
          val exactInfix: Exact<SelectWhereStage<Any, EntitySelection<Any>>> = infixType
          val exactFiltered: Exact<SelectWhereStage<Any, EntitySelection<Any>>> = filteredType
          val exactOrdered: Exact<SelectOrderStage<Any, EntitySelection<Any>>> = orderedType
          val exactPaged: Exact<SelectOffsetStage<Any, EntitySelection<Any>>> = pagedType
          val exactRows: Exact<List<Any>> = rowsType
          val exactFirst: Exact<CompiledFirst<Any>> = firstType
          val exactCount: CompiledCount = count
          val exactCursor: Exact<CompiledCursor<Any>> = cursorType
          val exactCaptured: Exact<List<Any>> = capturedType
        }
    """

    const val SOURCE_FREE_CONSUMER = """
        fun <T> CompiledSelect<T>.exact() = Exact<CompiledSelect<T>>()
        fun <T, S : Selection<T>> SelectProjectionStage<T, S>.exact() = Exact<SelectProjectionStage<T, S>>()
        fun <R, N : SqlNullability, T> SqlExpression<R, N, T>.exact() = Exact<SqlExpression<R, N, T>>()

        fun verify(table: Table<*>, connection: DbConnection) {
          val native = 42L.asValue()
          val literalLong = 42L.asLiteral()
          val literalInt = 42.asLiteral()
          val literalShort = 42.toShort()
              .asLiteral()
          val literalDouble = 1.25.asLiteral()
          val literalFloat = 1.25f.asLiteral()
          val literalString = "text".asLiteral()
          val literalBoolean = true.asLiteral()
          val literalByte = 42.toByte()
              .asLiteral()
          val literalBytes = byteArrayOf(1, 2)
              .asLiteral()
          val explicitLiteral = 42L.asLiteral(SqlType.LONG)
          val longLiteralType = literalLong.exact()
          val intLiteralType = literalInt.exact()
          val shortLiteralType = literalShort.exact()
          val doubleLiteralType = literalDouble.exact()
          val floatLiteralType = literalFloat.exact()
          val stringLiteralType = literalString.exact()
          val booleanLiteralType = literalBoolean.exact()
          val byteLiteralType = literalByte.exact()
          val bytesLiteralType = literalBytes.exact()
          val explicitLiteralType = explicitLiteral.exact()
          val exactLongLiteral: Exact<SqlExpression<Long, Required, Long>> = longLiteralType
          val exactIntLiteral: Exact<SqlExpression<Int, Required, Int>> = intLiteralType
          val exactShortLiteral: Exact<SqlExpression<Short, Required, Short>> = shortLiteralType
          val exactDoubleLiteral: Exact<SqlExpression<Double, Required, Double>> = doubleLiteralType
          val exactFloatLiteral: Exact<SqlExpression<Float, Required, Float>> = floatLiteralType
          val exactStringLiteral: Exact<SqlExpression<String, Required, String>> = stringLiteralType
          val exactBooleanLiteral: Exact<SqlExpression<Boolean, Required, Boolean>> = booleanLiteralType
          val exactByteLiteral: Exact<SqlExpression<Byte, Required, Byte>> = byteLiteralType
          val exactBytesLiteral: Exact<SqlExpression<ByteArray, Required, ByteArray>> = bytesLiteralType
          val exactExplicitLiteral: Exact<SqlExpression<Long, Required, Long>> = explicitLiteralType
          val initial = SELECT.COLUMN(native)
          val initialType = initial.exact()
          val exactInitial: Exact<SelectProjectionStage<Long, ScalarSelection<Long, Required, Long>>> = initialType
          val sharedQueryType: SelectQuery<Long, ScalarSelection<Long, Required, Long>> = initial
          val sharedConnected = sharedQueryType.usingConnection(connection)
          val sharedConnectedType = sharedConnected.exactType()
          val exactSharedConnected: Exact<SelectQuery<Long, ScalarSelection<Long, Required, Long>>> =
              sharedConnectedType
          val connectedDecoded = sharedConnected.readAs(SqlType.STRING)
          val connectedDecodedType = connectedDecoded.exactType()
          val exactConnectedDecoded: Exact<SelectQuery<String, ScalarSelection<Long, Required, String>>> =
              connectedDecodedType
          val requiredDecoded = initial.FROM(table)
              .WHERE(true.asValue())
              .LIMIT(1)
              .OFFSET(0)
              .readAs(SqlType.STRING)
          val nullableDecoded = SELECT.COLUMN(NULL)
              .LIMIT(1)
              .readAs(SqlType.STRING)
          val nullableConnected = nullableDecoded.usingConnection(connection)
          val requiredDecodedType = requiredDecoded.exactType()
          val nullableDecodedType = nullableDecoded.exactType()
          val nullableConnectedType = nullableConnected.exactType()
          val exactRequiredDecoded: Exact<SelectQuery<String, ScalarSelection<Long, Required, String>>> =
              requiredDecodedType
          val exactNullableDecoded: Exact<SelectQuery<String?, ScalarSelection<Nothing?, SqlNullable, String?>>> =
              nullableDecodedType
          val exactNullableConnected: Exact<SelectQuery<String?, ScalarSelection<Nothing?, SqlNullable, String?>>> =
              nullableConnectedType
          val fluent = SELECT.COLUMN(native).compile()
          val infix = (SELECT COLUMN native).compile()
          val nullable = SELECT.COLUMN((null as Long?).asValue()).compile()
          val replaced = SELECT.COLUMN(NULL).readAs(SqlType.LONG).compile()
          val first = SELECT.COLUMN(native).LIMIT(-1L).OFFSET(0L).takeFirst()
          val count = SELECT.COLUMN(native).LIMIT(0).count()
          val parameter = (null as Long?).asValue().asParameter("optional")
          val bound = SELECT.COLUMN(parameter).compile().bind(parameter TO null)
          val a = fluent.exact()
          val b = infix.exact()
          val c = nullable.exact()
          val d = replaced.exact()
          val e = bound.exact()
          val exactFluent: Exact<CompiledSelect<Long>> = a
          val exactInfix: Exact<CompiledSelect<Long>> = b
          val exactNullable: Exact<CompiledSelect<Long?>> = c
          val exactReplaced: Exact<CompiledSelect<Long?>> = d
          val exactBound: Exact<CompiledSelect<Long?>> = e
          val sharedPlanType: SelectPlan<List<Long>> = fluent
          val firstType: CompiledFirst<Long> = first
          val countType: CompiledCount = count
        }
    """

    const val CLAUSE_SEQUENCE_CONSUMER = """
        import com.siimkinks.sqlitemagic.Column
        import com.siimkinks.sqlitemagic.GREATER_THAN
        import com.siimkinks.sqlitemagic.IS

        fun verify(table: Table<Any>, age: Column<Any, Int, Required, Int, Int, Required, Int>) {
          val fluent = SELECT.COLUMN(age)
              .FROM(table)
              .WHERE(age IS 1)
              .GROUP_BY(age)
              .HAVING(age GREATER_THAN 0)
              .ORDER_BY(age.ASC())
              .LIMIT(1)
              .OFFSET(0)
          val groupedWithoutWhere = SELECT.COLUMN(age)
              .FROM(table)
              .GROUP_BY(age)
              .HAVING(age GREATER_THAN 0)
              .LIMIT(1)
          val orderedPage = SELECT.COLUMN(age)
              .FROM(table)
              .ORDER_BY(age.ASC())
              .LIMIT(2)
              .OFFSET(1)
          val infix = SELECT COLUMN age FROM table WHERE (age IS 1) GROUP_BY age HAVING
              (age GREATER_THAN 0) ORDER_BY age.ASC() LIMIT 1 OFFSET 0
        }
    """
  }
}
