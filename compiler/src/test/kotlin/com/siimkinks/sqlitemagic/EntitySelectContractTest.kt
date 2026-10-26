package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.readDriverSupport
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertNoWarnings
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class EntitySelectContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generated tables select their default entities and read them warning free from a start column`() {
    val generated = compiler
      .compile(
        SourceFile.kotlin(
          name = "EntityModels.kt",
          contents = """
            package fixtures.entity

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Author(
              @Id val id: Long? = null,
              val name: String? = null,
              val age: Int = 0
            )

            @Table
            data class Book(
              @Id val id: Long? = null,
              val title: String,
              val author: Author
            )

            @Table
            data class Owner(
              @Id val id: Long? = null,
              val name: String
            )

            @Table
            data class Pet(
              @Id val id: Long? = null,
              val owner: Owner
            )

            @Table
            data class Household(
              @Id val id: Long? = null,
              val first: Owner,
              val second: Owner,
              val author: Author
            )
          """
        ),
        readDriverSupport(),
        SourceFile.kotlin(
          name = "EntityReadDriver.kt",
          contents = """
            @file:OptIn(InternalSqliteMagicApi::class)

            package fixtures.entity

            import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
            import com.siimkinks.sqlitemagic.SqliteMagic_Author_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Book_Dao
            import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
            import fixtures.driver.cursor
            import fixtures.driver.unjoined

            object EntityReadDriver {
              @JvmStatic
              fun read(): List<Any> = listOf(
                SqliteMagic_Author_Dao.objectFromCursorPosition(
                  cursor = cursor("skipped", 7L, "Ada", 36L),
                  startIndex = 1
                ),
                SqliteMagic_Author_Dao.objectFromCursorPosition(
                  cursor = cursor(8L, null, 17L),
                  startIndex = 0
                ),
                SqliteMagic_Book_Dao.objectFromCursorPosition(
                  cursor = cursor("skipped", "skipped", 1L, "Dune", 7L),
                  startIndex = 2,
                  related = unjoined(BOOK)
                )
              )
            }
          """
        )
      )
      .isOk()
      .withGeneratedSource("AuthorTable.kt") {
        it
          .assertContainsUnwrapped(
            "override val entityFields: List<Column<Author, *, *, *, *, *, *>> = listOf(ID, NAME, AGE)",
            "related: RelatedEntities?",
            "SqliteMagic_Author_Dao.objectFromCursorPosition(cursor, startIndex)"
          )
          .assertDoesNotContain("relationships")
      }
      .withGeneratedSource("BookTable.kt") {
        it.assertContains(
          "override val relationships: List<TableRelationship>",
          """columnName = "author"""",
          "target = { AuthorTable.AUTHOR }",
          """targetIdColumnName = "id"""",
          "nullable = false",
          "read = RelationshipRead.DEEP",
          "SqliteMagic_Book_Dao.objectFromCursorPosition(cursor, startIndex, checkNotNull(related))"
        )
      }
      .withGeneratedSource("PetTable.kt") {
        it.assertContainsUnwrapped(
          "override val entityFields: List<Column<Pet, *, *, *, *, *, *>> = listOf(ID, OWNER)",
          "read = RelationshipRead.ALWAYS",
          "SqliteMagic_Pet_Dao.objectFromCursorPosition(cursor, startIndex, checkNotNull(related))"
        )
      }
      .withGeneratedSource("SqliteMagic_Author_Dao.kt") {
        it.assertContains("fun objectFromCursorPosition(cursor: Cursor, startIndex: Int): Author")
      }
      .withGeneratedSource("SqliteMagic_Book_Dao.kt") {
        it.assertContains(
          "related: RelatedEntities,",
          "related.read(cursor, 0) as Author?",
          "SqliteMagic_Author_Dao.newInstanceWithOnlyId("
        )
      }
      .withGeneratedSource("SqliteMagic_Pet_Dao.kt") {
        it.assertContains(
          "related: RelatedEntities,",
          "related.read(cursor, 0) as Owner"
        )
      }
      .assertNoWarnings(
        "SqliteMagic_Author_Dao.kt",
        "SqliteMagic_Book_Dao.kt",
        "SqliteMagic_Owner_Dao.kt",
        "SqliteMagic_Pet_Dao.kt",
        "SqliteMagic_Household_Dao.kt"
      )

    assertThat(
      generated
        .loadClass("fixtures.entity.EntityReadDriver")
        .getMethod("read")
        .invoke(null)
        .toString()
    )
      .isEqualTo(
        "[Author(id=7, name=Ada, age=36), Author(id=8, name=null, age=17), " +
            "Book(id=1, title=Dune, author=Author(id=7, name=null, age=0))]"
      )

    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "EntityConsumer.kt",
          contents = """
            package downstream.entity

            import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
            import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
            import com.siimkinks.sqlitemagic.CompiledFirst
            import com.siimkinks.sqlitemagic.EntitySelection
            import com.siimkinks.sqlitemagic.GREATER_THAN
            import com.siimkinks.sqlitemagic.IS
            import com.siimkinks.sqlitemagic.PetTable.Companion.PET
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.SelectFromStage
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.SelectWhereStage
            import com.siimkinks.sqlitemagic.queryDeep
            import fixtures.entity.Author
            import fixtures.entity.Book
            import fixtures.entity.Pet
            import typewitness.Exact
            import typewitness.exactType

            fun verify() {
              val adults = (SELECT FROM AUTHOR) WHERE (AUTHOR.AGE GREATER_THAN 18)
              val aliased = AUTHOR AS "writer"
              val aliasedAuthors = SELECT.FROM(aliased)
                  .WHERE(aliased.AGE GREATER_THAN 18)
              val pets = SELECT.FROM(PET)
              val deepBooks = (SELECT FROM BOOK WHERE (BOOK.TITLE IS "Dune")).queryDeep()
              val firstDeepPet = SELECT.FROM(PET)
                  .queryDeep()
                  .takeFirst()
              val adultsType = adults.exactType()
              val aliasedAuthorsType = aliasedAuthors.exactType()
              val petsType = pets.exactType()
              val deepBooksType = deepBooks.exactType()
              val firstDeepPetType = firstDeepPet.exactType()
              val exactAdults: Exact<SelectWhereStage<Author, EntitySelection<Author>>> = adultsType
              val exactAliasedAuthors: Exact<SelectWhereStage<Author, EntitySelection<Author>>> = aliasedAuthorsType
              val exactPets: Exact<SelectFromStage<Pet, EntitySelection<Pet>>> = petsType
              val exactDeepBooks: Exact<SelectQuery<Book, EntitySelection<Book>>> = deepBooksType
              val exactFirstDeepPet: Exact<CompiledFirst<Pet>> = firstDeepPetType
            }
          """
        ),
        classpaths = listOf(generated.outputDirectory)
      )
      .isOk()
  }
}
