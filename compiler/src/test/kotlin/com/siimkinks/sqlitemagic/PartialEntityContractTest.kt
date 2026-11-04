package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.readDriverSupport
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class PartialEntityContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generated tables declare partial requirements and read partial entities from selected positions`() {
    val generated = compiler
      .compile(
        SourceFile.kotlin(
          name = "PartialModels.kt",
          contents = """
            package fixtures.partial

            import com.siimkinks.sqlitemagic.annotation.Embedded
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
            data class Publisher(
              @Id val id: Long? = null,
              val name: String
            )

            @Table
            data class Edition(
              @Id val id: Long? = null,
              val publisher: Publisher
            )

            data class Contact(
              val city: String,
              val zip: Int?
            )

            @Table
            data class Reader(
              @Id val id: Long? = null,
              @Embedded(prefix = "contact_") val contact: Contact?
            )

            @Table
            class Draft {
              @Id
              var id: Long? = null
              var title: String = "untitled"
              var note: String? = "seed"
            }

            data class Rank(
              val label: String,
              val level: Int
            )

            @Table
            class Profile {
              @Id
              var id: Long? = null
              @Embedded(prefix = "rank_")
              var rank: Rank = Rank(
                label = "seed",
                level = 1
              )
            }

            @Table
            class Shelf {
              @Id
              var id: Long? = null
              var owner: Author? = Author(
                id = 9L,
                name = "seed"
              )
            }

            @Table
            class Rack {
              @Id
              var id: Long? = null
              var owner: Author = Author(
                id = 9L,
                name = "seed"
              )
            }

            @Table
            class Crate {
              @Id
              var id: Long? = null
              var publisher: Publisher = Publisher(
                id = 3L,
                name = "seed"
              )
            }
          """
        ),
        readDriverSupport(),
        SourceFile.kotlin(
          name = "PartialReadDriver.kt",
          contents = """
            @file:OptIn(InternalSqliteMagicApi::class)

            package fixtures.partial

            import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
            import com.siimkinks.sqlitemagic.CrateTable.Companion.CRATE
            import com.siimkinks.sqlitemagic.RackTable.Companion.RACK
            import com.siimkinks.sqlitemagic.ShelfTable.Companion.SHELF
            import com.siimkinks.sqlitemagic.SqliteMagic_Author_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Book_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Crate_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Draft_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Profile_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Rack_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Reader_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_Shelf_Dao
            import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
            import fixtures.driver.row
            import fixtures.driver.unjoined

            object PartialReadDriver {

              @JvmStatic
              fun read(): List<String> {
                val draft = SqliteMagic_Draft_Dao.objectFromSelectedPositions(
                  statement = row(5L),
                  positions = intArrayOf(0, -1, -1)
                )
                val profiles = listOf(
                  SqliteMagic_Profile_Dao.objectFromSelectedPositions(
                    statement = row(6L),
                    positions = intArrayOf(0, -1, -1)
                  ),
                  SqliteMagic_Profile_Dao.objectFromSelectedPositions(
                    statement = row(6L, "gold", 3L),
                    positions = intArrayOf(0, 1, 2)
                  )
                )
                val shelves = listOf(
                  SqliteMagic_Shelf_Dao.objectFromSelectedPositions(
                    statement = row(8L),
                    positions = intArrayOf(0, -1),
                    related = unjoined(SHELF)
                  ),
                  SqliteMagic_Shelf_Dao.objectFromSelectedPositions(
                    statement = row(8L, null),
                    positions = intArrayOf(0, 1),
                    related = unjoined(SHELF)
                  ),
                  SqliteMagic_Shelf_Dao.objectFromSelectedPositions(
                    statement = row(8L, 7L),
                    positions = intArrayOf(0, 1),
                    related = unjoined(SHELF)
                  )
                )
                val racks = listOf(
                  SqliteMagic_Rack_Dao.objectFromSelectedPositions(
                    statement = row(10L),
                    positions = intArrayOf(0, -1),
                    related = unjoined(RACK)
                  ),
                  SqliteMagic_Rack_Dao.objectFromSelectedPositions(
                    statement = row(10L, 7L),
                    positions = intArrayOf(0, 1),
                    related = unjoined(RACK)
                  )
                )
                val crate = SqliteMagic_Crate_Dao.objectFromSelectedPositions(
                  statement = row(11L),
                  positions = intArrayOf(0, -1),
                  related = unjoined(CRATE)
                )
                return listOf(
                  SqliteMagic_Author_Dao.objectFromSelectedPositions(
                    statement = row("skipped", "Ada", 7L, 36L),
                    positions = intArrayOf(2, 1, 3)
                  ).toString(),
                  SqliteMagic_Book_Dao.objectFromSelectedPositions(
                    statement = row(3L, "Dune", 7L),
                    positions = intArrayOf(0, 1, 2),
                    related = unjoined(BOOK)
                  ).toString(),
                  SqliteMagic_Reader_Dao.objectFromSelectedPositions(
                    statement = row(4L, "Tartu", null),
                    positions = intArrayOf(0, 1, 2)
                  ).toString(),
                  SqliteMagic_Reader_Dao.objectFromSelectedPositions(
                    statement = row(4L),
                    positions = intArrayOf(0, -1, -1)
                  ).toString(),
                  "Draft(" + draft.id + ", " + draft.title + ", " + draft.note + ")"
                ) + profiles.map { profile -> "Profile(" + profile.id + ", " + profile.rank + ")" } +
                    shelves.map { shelf -> "Shelf(" + shelf.id + ", " + shelf.owner + ")" } +
                    racks.map { rack -> "Rack(" + rack.id + ", " + rack.owner + ")" } +
                    listOf("Crate(" + crate.id + ", " + crate.publisher + ")")
              }
            }
          """
        )
      )
      .isOk()
      .withGeneratedSource("BookTable.kt") {
        it.assertContainsUnwrapped(
          "override val entityProperties: List<EntityProperty>",
          """EntityProperty(path = "Book.id", required = false)""",
          """EntityProperty(path = "Book.title", required = true)""",
          """EntityProperty(path = "Book.author", required = true)""",
          "override fun readPartialEntity(",
          "SqliteMagic_Book_Dao.objectFromSelectedPositions(statement, positions, checkNotNull(related))"
        )
      }
      .withGeneratedSource("ReaderTable.kt") {
        it.assertContainsUnwrapped(
          """EntityProperty(path = "Reader.contact.city", required = true, within = "Reader.contact")""",
          """EntityProperty(path = "Reader.contact.zip", required = false)"""
        )
      }
      .withGeneratedSource("ProfileTable.kt") {
        it.assertContainsUnwrapped(
          """EntityProperty(path = "Profile.rank.label", required = true, within = "Profile.rank")"""
        )
      }
      .withGeneratedSource("DraftTable.kt") {
        it.assertContainsUnwrapped(
          """EntityProperty(path = "Draft.title", required = false)""",
          "SqliteMagic_Draft_Dao.objectFromSelectedPositions(statement, positions)"
        )
      }
      .withGeneratedSource("SqliteMagic_Book_Dao.kt") {
        it.assertContains(
          "fun objectFromSelectedPositions(",
          "positions: IntArray",
          "related: RelatedEntities,",
          "related.readSelected(statement, 0) as Author?",
          "SqliteMagic_Author_Dao.newInstanceWithOnlyId("
        )
      }
      .withGeneratedSource("SqliteMagic_Edition_Dao.kt") {
        it
          .functionBody("objectFromSelectedPositions")
          .assertContainsUnwrapped(
            """publisher = (related.readSelected(statement, 0) as Publisher?) ?: throw SQLException(""" +
                """if (columnIndex1 < 0 || statement.isNull(columnIndex1)) """ +
                """"Required recursive relationship \"publisher\" had a NULL ID" """ +
                """else "Required recursive relationship \"publisher\" referenced a missing row")"""
          )
      }
      .withGeneratedSource("SqliteMagic_Rack_Dao.kt") {
        it
          .functionBody("objectFromSelectedPositions")
          .assertContainsUnwrapped(
            """((related.readSelected(statement, 0) as Author?) ?: (if (columnIndex1 < 0) null else """ +
                """if (columnIndex1 >= 0 && statement.isNull(columnIndex1)) throw SQLException("Required """ +
                """recursive relationship \"owner\" had a """ +
                """NULL ID") else SqliteMagic_Author_Dao.newInstanceWithOnlyId(statement.getLong(columnIndex1))))""" +
                """?.let { this.owner = it }"""
          )
      }
      .withGeneratedSource("SqliteMagic_Crate_Dao.kt") {
        it
          .functionBody("objectFromSelectedPositions")
          .assertContainsUnwrapped(
            """((related.readSelected(statement, 0) as Publisher?) ?: (if (columnIndex1 < 0) null else throw """ +
                """SQLException(if (columnIndex1 >= 0 && statement.isNull(columnIndex1)) "Required recursive """ +
                """relationship \"publisher\" had a NULL """ +
                """ID" else "Required recursive relationship \"publisher\" referenced a missing row")))""" +
                """?.let { this.publisher = it }"""
          )
      }

    assertThat(
      generated
        .loadClass("fixtures.partial.PartialReadDriver")
        .getMethod("read")
        .invoke(null)
    )
      .isEqualTo(
        listOf(
          "Author(id=7, name=Ada, age=36)",
          "Book(id=3, title=Dune, author=Author(id=7, name=null, age=0))",
          "Reader(id=4, contact=Contact(city=Tartu, zip=null))",
          "Reader(id=4, contact=null)",
          "Draft(5, untitled, seed)",
          "Profile(6, Rank(label=seed, level=1))",
          "Profile(6, Rank(label=gold, level=3))",
          "Shelf(8, Author(id=9, name=seed, age=0))",
          "Shelf(8, Author(id=9, name=seed, age=0))",
          "Shelf(8, Author(id=7, name=null, age=0))",
          "Rack(10, Author(id=9, name=seed, age=0))",
          "Rack(10, Author(id=7, name=null, age=0))",
          "Crate(11, Publisher(id=3, name=seed))"
        )
      )
  }
}
