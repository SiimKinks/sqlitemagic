package com.example

import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import org.junit.Test

class DeleteTest : DSLTests {
  @Test
  fun deleteFromBuilder() {
    (DELETE FROM MAGAZINE).isEqualTo("DELETE FROM magazine ")
  }

  @Test
  fun deleteFromBuilderWithAlias() {
    (DELETE FROM (MAGAZINE AS "foo")).isEqualTo("DELETE FROM magazine ")
  }

  @Test
  fun deleteRawFromBuilder() {
    (DELETE FROM "magazine").isEqualTo("DELETE FROM magazine ")
  }

  @Test
  fun deleteWhereBuilder() {
    (DELETE
        FROM MAGAZINE
        WHERE (MAGAZINE.NAME IS "asd"))
        .isEqualTo(
            expectedSql = "DELETE FROM magazine WHERE magazine.name=? ",
            withArgs = *arrayOf("asd"))

    (DELETE
        FROM MAGAZINE
        WHERE (
        ((MAGAZINE.NAME IS "asd") AND MAGAZINE.NAME.isNotNull)
            OR (MAGAZINE.NR_OF_RELEASES GREATER_THAN 2)))
        .isEqualTo("DELETE " +
            "FROM magazine " +
            "WHERE (" +
            "(magazine.name=? AND magazine.name IS NOT NULL) " +
            "OR magazine.nr_of_releases>?) ",
            "asd", "2")

    (DELETE
        FROM MAGAZINE
        WHERE (
        ((MAGAZINE.NAME IS "asd") AND MAGAZINE.NAME.isNotNull)
            AND (MAGAZINE.NR_OF_RELEASES IS 2)))
        .isEqualTo("DELETE " +
            "FROM magazine " +
            "WHERE (" +
            "(magazine.name=? AND magazine.name IS NOT NULL) " +
            "AND magazine.nr_of_releases=?) ",
            "asd", "2")

    (DELETE
        FROM MAGAZINE
        WHERE (
        ((((MAGAZINE.NAME IS "asd") AND MAGAZINE.NAME.isNotNull)
            AND (MAGAZINE.NR_OF_RELEASES IS 2))
            AND (((MAGAZINE.ID IS 2) OR MAGAZINE.AUTHOR.isNotNull) OR (MAGAZINE.NR_OF_RELEASES LESS_THAN 55)))
            OR (((MAGAZINE.ID GREATER_THAN 2) AND MAGAZINE.AUTHOR.isNull) AND (MAGAZINE.NR_OF_RELEASES IS_NOT 55))))
        .isEqualTo("DELETE " +
            "FROM magazine " +
            "WHERE ((((magazine.name=? AND magazine.name IS NOT NULL) AND magazine.nr_of_releases=?) " +
            "AND ((magazine.id=? OR magazine.author IS NOT NULL) OR magazine.nr_of_releases<?)) " +
            "OR ((magazine.id>? AND magazine.author IS NULL) AND magazine.nr_of_releases!=?)) ",
            "asd", "2", "2", "55", "2", "55")
  }

  @Test
  fun deleteRawWhereBuilder() {
    (DELETE
        FROM "book"
        WHERE "book.title IS NOT NULL")
        .isEqualTo("DELETE FROM book WHERE book.title IS NOT NULL ")
  }

  @Test
  fun deleteRawWhereWithArgsBuilder() {
    (DELETE
        FROM "book"
        WHERE ("book.title=?" to arrayOf("foo")))
        .isEqualTo("DELETE FROM book WHERE book.title=? ", "foo")
  }
}