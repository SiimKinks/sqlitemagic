package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class RawQueryContractTest {
  @Test
  fun `whole raw queries, cursors and statements keep their declared types`() {
    PlainKotlinCompilation
      .compile(
        fileName = "RawQueryConsumer.kt",
        contents = """
          package downstream.raw

          import android.database.Cursor
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.CompiledMutation
          import com.siimkinks.sqlitemagic.CompiledRawCursor
          import com.siimkinks.sqlitemagic.CompiledRawFirst
          import com.siimkinks.sqlitemagic.CompiledRawReadCursor
          import com.siimkinks.sqlitemagic.CompiledRawSelect
          import com.siimkinks.sqlitemagic.DELETE
          import com.siimkinks.sqlitemagic.DbConnection
          import com.siimkinks.sqlitemagic.DeclaredTables
          import com.siimkinks.sqlitemagic.RawColumnQuery
          import com.siimkinks.sqlitemagic.RawMutationQuery
          import com.siimkinks.sqlitemagic.RawSelect
          import com.siimkinks.sqlitemagic.RawSelectQuery
          import com.siimkinks.sqlitemagic.Row2
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPDATE
          import com.siimkinks.sqlitemagic.UndeclaredTables
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.rxjava2.ListQueryObservable
          import com.siimkinks.sqlitemagic.rxjava2.SingleItemQueryObservable
          import com.siimkinks.sqlitemagic.rxjava2.observe
          import io.reactivex.Maybe
          import io.reactivex.Single
          import typewitness.Exact
          import typewitness.exactType

          class Author
          data class AuthorName(val id: Long, val name: String?)
          data class AuthorId(val value: Long)

          fun verify(
            author: Table<Author>,
            book: Table<Any>,
            connection: DbConnection,
            name: Column<Author, String, SqlNullable, String?, String, SqlNullable, String?>
          ) {
            val unread = SELECT.RAW("SELECT id, name FROM author WHERE id > ?", 10L)
            val infix = SELECT RAW "SELECT 1"
            val declared = unread.observes(author)
            val rows = declared.readAs(SqlType.LONG, SqlType.STRING.nullable)
            val names = rows.mapTo(::AuthorName)
            val lambdaNames = rows.mapTo { id, value -> AuthorName(id = id, name = value) }
            val templated = SELECT
                .RAW("SELECT name FROM author")
                .readAs(name)
            val ids = SELECT
                .RAW("SELECT id FROM author")
                .readAs(SqlType.LONG)
            val connected = ids
                .usingConnection(connection)
                .observes(author, book)
            val authorIds = ids.mapTo(::AuthorId)
            val connectedIds = connected.mapTo { AuthorId(it) }
            val list = names.execute()
            val first = names.takeFirst()
            val firstValue = first.execute()
            val cursorPlan = SELECT
                .RAW("PRAGMA index_list('author')")
                .toCursor()
            val cursor = cursorPlan.execute()
            val readCursor = names.toCursor()
            val cursorName = readCursor.getFromCurrentPosition(cursor)
            val compiled = names.compile()
            val streamed = names.observe()
            val compiledStream = compiled.observe()
            val once = ids.observe()
            val compiledOnce = ids
                .compile()
                .observe()
            val firstOnce = ids
                .takeFirst()
                .observe()
            val firstStream = names
                .takeFirst()
                .observe()
            val cursorOnce = cursorPlan.observe()
            val cursorStream = declared
                .toCursor()
                .observe()
            val readCursorOnce = ids
                .toCursor()
                .observe()
            val update = UPDATE.RAW("UPDATE author SET name = ? WHERE id = ?", "Ada", 1L)
            val delete = DELETE RAW "DELETE FROM legacy_log"
            val notified = update
                .notifies(author)
                .usingConnection(connection)
            val affected = notified.execute()
            val affectedOnce = delete.observe()
            val compiledMutation = notified.compile()

            val unreadType = unread.exactType()
            val exactUnread: Exact<RawSelect<UndeclaredTables>> = unreadType
            val infixType = infix.exactType()
            val exactInfix: Exact<RawSelect<UndeclaredTables>> = infixType
            val declaredType = declared.exactType()
            val exactDeclared: Exact<RawSelect<DeclaredTables>> = declaredType
            val rowsType = rows.exactType()
            val exactRows: Exact<RawSelectQuery<Row2<Long, String?>, DeclaredTables>> = rowsType
            val namesType = names.exactType()
            val exactNames: Exact<RawSelectQuery<AuthorName, DeclaredTables>> = namesType
            val lambdaNamesType = lambdaNames.exactType()
            val exactLambdaNames: Exact<RawSelectQuery<AuthorName, DeclaredTables>> = lambdaNamesType
            val templatedType = templated.exactType()
            val exactTemplated: Exact<RawColumnQuery<String?, UndeclaredTables>> = templatedType
            val idsType = ids.exactType()
            val exactIds: Exact<RawColumnQuery<Long, UndeclaredTables>> = idsType
            val connectedType = connected.exactType()
            val exactConnected: Exact<RawColumnQuery<Long, DeclaredTables>> = connectedType
            val authorIdsType = authorIds.exactType()
            val exactAuthorIds: Exact<RawSelectQuery<AuthorId, UndeclaredTables>> = authorIdsType
            val connectedIdsType = connectedIds.exactType()
            val exactConnectedIds: Exact<RawSelectQuery<AuthorId, DeclaredTables>> = connectedIdsType
            val listType = list.exactType()
            val exactList: Exact<List<AuthorName>> = listType
            val firstType = first.exactType()
            val exactFirst: Exact<CompiledRawFirst<AuthorName, DeclaredTables>> = firstType
            val firstValueType = firstValue.exactType()
            val exactFirstValue: Exact<AuthorName?> = firstValueType
            val cursorPlanType = cursorPlan.exactType()
            val exactCursorPlan: Exact<CompiledRawCursor<UndeclaredTables>> = cursorPlanType
            val cursorType = cursor.exactType()
            val exactCursor: Exact<Cursor> = cursorType
            val readCursorType = readCursor.exactType()
            val exactReadCursor: Exact<CompiledRawReadCursor<AuthorName, DeclaredTables>> = readCursorType
            val cursorNameType = cursorName.exactType()
            val exactCursorName: Exact<AuthorName> = cursorNameType
            val compiledType = compiled.exactType()
            val exactCompiled: Exact<CompiledRawSelect<AuthorName, DeclaredTables>> = compiledType
            val streamedType = streamed.exactType()
            val exactStreamed: Exact<ListQueryObservable<AuthorName>> = streamedType
            val compiledStreamType = compiledStream.exactType()
            val exactCompiledStream: Exact<ListQueryObservable<AuthorName>> = compiledStreamType
            val onceType = once.exactType()
            val exactOnce: Exact<Single<List<Long>>> = onceType
            val compiledOnceType = compiledOnce.exactType()
            val exactCompiledOnce: Exact<Single<List<Long>>> = compiledOnceType
            val firstOnceType = firstOnce.exactType()
            val exactFirstOnce: Exact<Maybe<Long>> = firstOnceType
            val firstStreamType = firstStream.exactType()
            val exactFirstStream: Exact<SingleItemQueryObservable<AuthorName?>> = firstStreamType
            val cursorOnceType = cursorOnce.exactType()
            val exactCursorOnce: Exact<Single<Cursor>> = cursorOnceType
            val cursorStreamType = cursorStream.exactType()
            val exactCursorStream: Exact<SingleItemQueryObservable<Cursor>> = cursorStreamType
            val readCursorOnceType = readCursorOnce.exactType()
            val exactReadCursorOnce: Exact<Single<Cursor>> = readCursorOnceType
            val updateType = update.exactType()
            val exactUpdate: Exact<RawMutationQuery> = updateType
            val deleteType = delete.exactType()
            val exactDelete: Exact<RawMutationQuery> = deleteType
            val affectedType = affected.exactType()
            val exactAffected: Exact<Int> = affectedType
            val affectedOnceType = affectedOnce.exactType()
            val exactAffectedOnce: Exact<Single<Int>> = affectedOnceType
            val compiledMutationType = compiledMutation.exactType()
            val exactCompiledMutation: Exact<CompiledMutation> = compiledMutationType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      """unread raw query execution | val invalid = SELECT.RAW("SELECT 1").execute() | 'execute'""",
      """unread raw query compilation | val invalid = SELECT.RAW("SELECT 1").compile() | 'compile'""",
      """raw count | val invalid = SELECT.RAW("SELECT 1").readAs(SqlType.LONG).count() | 'count'""",
      """raw clause | val invalid = SELECT.RAW("SELECT 1").readAs(SqlType.LONG).LIMIT(1) | 'LIMIT'""",
      """raw plan parameters | val invalid = SELECT.RAW("SELECT 1").readAs(SqlType.LONG).compile().bind() | 'bind'""",
      """mapping before readAs | val invalid = SELECT.RAW("SELECT 1, 2").mapTo(::Pair) | """ +
          "None of the following candidates is applicable",
      "row callback on a raw row | " +
          """val invalid = SELECT.RAW("SELECT 1, 2").readAs(SqlType.LONG, SqlType.LONG)""" +
          ".mapTo { row -> row } | Argument type mismatch",
      "table mapping of a raw row | " +
          """val invalid = SELECT.RAW("SELECT 1, 2").readAs(SqlType.LONG, SqlType.LONG)""" +
          ".mapTo(author) | mapTo",
      """second mapping of a raw column | val invalid = SELECT.RAW("SELECT 1").readAs(SqlType.LONG)""" +
          ".mapTo { id -> id + 1 }.mapTo { id -> id } | mapTo",
      """table mapping of a raw column | val invalid = SELECT.RAW("SELECT 1").readAs(SqlType.LONG).mapTo(author) | """ +
          "Argument type mismatch",
      "raw query as a selected value | " +
          """val invalid = SELECT.COLUMN(SELECT.RAW("SELECT 1").readAs(SqlType.LONG)) | Argument type mismatch""",
      """empty table declaration | val invalid = SELECT.RAW("SELECT 1").observes() | 'table'""",
      """raw statement parameters | val invalid = UPDATE.RAW("UPDATE author SET name = 'x'").bind() | 'bind'""",
      """empty notification | val invalid = DELETE.RAW("DELETE FROM author").notifies() | 'table'"""
    ]
  )
  fun `raw queries and statements outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedRawQuery.kt",
        contents = """
          package downstream.raw

          import com.siimkinks.sqlitemagic.DELETE
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPDATE
          import com.siimkinks.sqlitemagic.mapTo

          class Author

          fun verify(author: Table<Author>) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
