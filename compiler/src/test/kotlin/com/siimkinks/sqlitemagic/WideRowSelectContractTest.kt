package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class WideRowSelectContractTest {
  @Test
  fun `any-width selections, field lists, lookups, callbacks and table mappings keep their types`() {
    PlainKotlinCompilation
      .compile(
        fileName = "WideRowConsumer.kt",
        contents = """
          package downstream.widerow

          import com.siimkinks.sqlitemagic.CompiledFirst
          import com.siimkinks.sqlitemagic.DbConnection
          import com.siimkinks.sqlitemagic.PartialEntitySelection
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.Row2
          import com.siimkinks.sqlitemagic.RowSelection
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SelectFromStage
          import com.siimkinks.sqlitemagic.SelectProjectionStage
          import com.siimkinks.sqlitemagic.SelectQuery
          import com.siimkinks.sqlitemagic.SelectedRow
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.SqlOutput
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.TableFields
          import com.siimkinks.sqlitemagic.mapTo
          import typewitness.Exact
          import typewitness.exactType

          class Author

          fun verify(
            author: Table<Author>,
            connection: DbConnection,
            id: SqlExpression<Long, Required, Long>,
            name: SqlExpression<String, SqlNullable, String?>
          ) {
            val fields = author.all()
            val outputs: Array<SqlOutput> = arrayOf(id, fields)
            val wide = SELECT
                .COLUMNS(id, name, author.all())
                .FROM(author)
            val single = SELECT.COLUMNS(author.all())
            val spread = SELECT.COLUMNS(*outputs)
            val distinct = SELECT.DISTINCT(id, author.all())
            val pair = SELECT.COLUMNS(id, name)
            val rows = wide
                .usingConnection(connection)
                .execute()
            val first = rows.first()
            val firstId = first[id]
            val firstName = first[name]
            val labels = wide.mapTo { it[name] ?: it[id].toString() }
            val fromWide = wide.mapTo(author)
            val fromPair = pair
                .FROM(author)
                .mapTo(author)
            val fromScalar = SELECT
                .COLUMN(id)
                .FROM(author)
                .mapTo(author)
            val firstAuthor = fromWide.takeFirst()

            val fieldsType = fields.exactType()
            val exactFields: Exact<TableFields> = fieldsType
            val wideType = wide.exactType()
            val exactWide: Exact<SelectFromStage<SelectedRow, RowSelection<SelectedRow>>> = wideType
            val singleType = single.exactType()
            val exactSingle: Exact<SelectProjectionStage<SelectedRow, RowSelection<SelectedRow>>> = singleType
            val spreadType = spread.exactType()
            val exactSpread: Exact<SelectProjectionStage<SelectedRow, RowSelection<SelectedRow>>> = spreadType
            val distinctType = distinct.exactType()
            val exactDistinct: Exact<SelectProjectionStage<SelectedRow, RowSelection<SelectedRow>>> =
                distinctType
            val pairType = pair.exactType()
            val exactPair: Exact<SelectProjectionStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                pairType
            val rowsType = rows.exactType()
            val exactRows: Exact<List<SelectedRow>> = rowsType
            val firstIdType = firstId.exactType()
            val exactFirstId: Exact<Long> = firstIdType
            val firstNameType = firstName.exactType()
            val exactFirstName: Exact<String?> = firstNameType
            val labelsType = labels.exactType()
            val exactLabels: Exact<SelectQuery<String, RowSelection<String>>> = labelsType
            val fromWideType = fromWide.exactType()
            val exactFromWide: Exact<SelectQuery<Author, PartialEntitySelection<Author>>> = fromWideType
            val fromPairType = fromPair.exactType()
            val exactFromPair: Exact<SelectQuery<Author, PartialEntitySelection<Author>>> = fromPairType
            val fromScalarType = fromScalar.exactType()
            val exactFromScalar: Exact<SelectQuery<Author, PartialEntitySelection<Author>>> = fromScalarType
            val firstAuthorType = firstAuthor.exactType()
            val exactFirstAuthor: Exact<CompiledFirst<Author>> = firstAuthorType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "field list as one value | val invalid = SELECT.COLUMN(author.all()) | Argument type mismatch",
      "field list in a predicate | val invalid = id IS author.all() | Argument type mismatch",
      "field list as a typed row element | val invalid = SELECT.COLUMNS(first = id, second = author.all()) | " +
          "Argument type mismatch",
      "row callback on a typed row | val invalid = SELECT.COLUMNS(id, id).mapTo { row -> row[id] } | " +
          "Cannot infer type",
      "table mapping of an entity query | val invalid = SELECT.FROM(author).mapTo(author) | " +
          "SelectQuery<T, RowSelection<T>>.mapTo(table: Table<M>): SelectQuery<M, PartialEntitySelection<M>>' " +
          "is inapplicable because of a receiver type mismatch"
    ]
  )
  fun `field lists and mappings outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedWideRow.kt",
        contents = """
          package downstream.widerow

          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.mapTo

          class Author

          fun verify(
            author: Table<Author>,
            id: SqlExpression<Long, Required, Long>
          ) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
