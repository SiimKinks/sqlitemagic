package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.TYPE_WITNESSES
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class EntityValueContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generated table objects are entity values of typed and wide rows`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "Models.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Author(
              @Id val id: Long? = null,
              val name: String,
              val nickname: String? = null
            )

            @Table
            data class Book(
              @Id val id: Long? = null,
              val title: String,
              val author: Author
            )
          """
        ),
        SourceFile.kotlin(
          name = "Consumer.kt",
          contents = """
            package consumer

            import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
            import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
            import com.siimkinks.sqlitemagic.COUNT
            import com.siimkinks.sqlitemagic.DbConnection
            import com.siimkinks.sqlitemagic.IS
            import com.siimkinks.sqlitemagic.Row2
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.mapTo
            import $PACKAGE.Author
            import $PACKAGE.Book
            import typewitness.Exact
            import typewitness.exactType

            data class AuthorWithCount(val author: Author, val bookCount: Long)

            fun verify(connection: DbConnection) {
              val authorsWithCounts = (
                  SELECT.COLUMNS(AUTHOR, COUNT(BOOK.ID))
                      FROM AUTHOR
                      LEFT_JOIN BOOK ON (BOOK.AUTHOR IS AUTHOR.ID)
                      GROUP_BY AUTHOR.ID
                  )
                .mapTo(::AuthorWithCount)
                .usingConnection(connection)
                .execute()
              val pairs = (SELECT.COLUMNS(BOOK, AUTHOR) FROM BOOK)
                .usingConnection(connection)
                .execute()
              val author = (SELECT.COLUMNS(AUTHOR, BOOK.all()) FROM BOOK)
                .usingConnection(connection)
                .execute()
                .first()[AUTHOR]

              val countsType = authorsWithCounts.exactType()
              val exactCounts: Exact<List<AuthorWithCount>> = countsType
              val pairsType = pairs.exactType()
              val exactPairs: Exact<List<Row2<Book, Author>>> = pairsType
              val authorType = author.exactType()
              val exactAuthor: Exact<Author> = authorType
            }
          """
        ),
        TYPE_WITNESSES
      )
      .isOk()
  }
}
