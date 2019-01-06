package com.siimkinks.sqlitemagic.query

import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.BookTable.BOOK
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.Book
import com.siimkinks.sqlitemagic.model.Magazine
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class CompoundTest {
  @Test
  fun unionSelect() =
      QueryTestCase(
          "Union select queries all distinct values",
          setUp = {
            createVals {
              Magazine.newRandom().insert().execute()
              Book.newRandom().insert().execute()
            }
          },
          operation = SelectListQueryOperation {
            Select.from(MAGAZINE)
                .union(Select.from(BOOK))
          },
          assertResults = { testVals, resultVals ->
            assertThat(resultVals.size).isEqualTo(testVals.size * 2)
          })
          .test()

  @Test
  fun unionSelectMultipleColumns() =
      QueryTestCase(
          "Union select from multiple columns queries all distinct values",
          setUp = {
            createVals {
              Magazine.newRandom().insert().execute()
              Book.newRandom().insert().execute()
            }
          },
          operation = SelectListQueryOperation {
            Select
                .columns(MAGAZINE.NAME, MAGAZINE.NR_OF_RELEASES)
                .from(MAGAZINE)
                .union(Select
                    .columns(BOOK.TITLE, BOOK.NR_OF_RELEASES)
                    .from(BOOK))
          },
          assertResults = { testVals, resultVals ->
            assertThat(resultVals.size).isEqualTo(testVals.size * 2)
          })
          .test()

  @Test
  fun unionSelectSingleColumn() =
      QueryTestCase(
          "Union select from single column queries all distinct values",
          setUp = {
            createVals {
              UUID.randomUUID().toString().also { name ->
                Magazine.newRandom(name).insert().execute()
                Book.newRandom(name).insert().execute()
              }
            }.apply { sort() }
          },
          operation = SelectListQueryOperation {
            Select
                .column(MAGAZINE.NAME)
                .from(MAGAZINE)
                .union(Select
                    .column(BOOK.TITLE)
                    .from(BOOK))
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @Test
  fun unionAllSelectSingleColumn() =
      QueryTestCase(
          "Union select from single column queries all values",
          setUp = {
            createVals {
              val magazineId = Magazine.newRandom().insert().execute()
              val book = Book.newRandom()
              book.baseId = magazineId
              book.insert().execute()
            }
          },
          operation = SelectListQueryOperation {
            Select
                .column(MAGAZINE._ID)
                .from(MAGAZINE)
                .unionAll(Select
                    .column(BOOK.BASE_ID)
                    .from(BOOK))
          },
          assertResults = { testVals, resultVals ->
            assertThat(resultVals.size).isEqualTo(testVals.size * 2)
          })
          .test()

  @Test
  fun unionSelectWithWhereClause() =
      QueryTestCase(
          "Union select queries all distinct values using WHERE clause",
          setUp = {
            List(10) { i ->
              when {
                i % 2 == 0 -> {
                  Magazine.newRandom("foo").insert().execute()
                  Book.newRandom("foo").insert().execute()
                }
                else -> {
                  Magazine.newRandom().insert().execute()
                  Book.newRandom().insert().execute()
                }
              }
            }
          },
          operation = SelectListQueryOperation {
            Select
                .from(MAGAZINE)
                .where(MAGAZINE.NAME.`is`("foo"))
                .union(Select
                    .from(BOOK)
                    .where(BOOK.TITLE.`is`("foo")))
          },
          assertResults = { testVals, resultVals ->
            assertThat(resultVals.size).isEqualTo(testVals.size)
          })
          .test()
}