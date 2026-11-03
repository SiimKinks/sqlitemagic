package com.siimkinks.sqlitemagic.framework

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class SchemaDependentColumnsTest {
  @Test
  fun `a star keeps the schema check unless it expands only a subquery that ends the FROM clause`() {
    val cases = mapOf(
      "star of a table" to ("SELECT * FROM item" to true),
      "qualified star" to ("SELECT item.* FROM item" to true),
      "DISTINCT star" to ("SELECT DISTINCT * FROM item" to true),
      "named columns" to ("SELECT id FROM item" to false),
      "insert with quoted names" to ("""INSERT INTO "item" ("id", "name") VALUES (?, ?)""" to false),
      "transaction control" to ("BEGIN IMMEDIATE" to false),
      "COUNT(*)" to ("SELECT COUNT(*) FROM item" to false),
      "RETURNING star" to ("INSERT INTO item VALUES (1) RETURNING *" to false),
      "RETURNING in a string" to ("SELECT * FROM item WHERE state = 'returning'" to true),
      "RETURNING in a quoted name" to ("""SELECT "returning", * FROM item""" to true),
      "RETURNING in a comment" to ("SELECT * FROM item -- returning" to true),
      "block comment before a star" to ("SELECT /* all */ * FROM item" to true),
      "line comment before a qualified star" to ("SELECT t. -- x\n* FROM item t" to true),
      "star in a string" to ("SELECT id, '*' FROM item" to false),
      "star in a comment" to ("SELECT id /* , * */ FROM item" to false),
      "star of a subquery at the end" to ("SELECT * FROM (SELECT id FROM item)" to false),
      "star of a compound arm" to ("SELECT 1 UNION SELECT * FROM (SELECT id FROM t LIMIT 1) EXCEPT SELECT 2" to false),
      "star of a subquery before WHERE" to ("SELECT * FROM (SELECT id FROM item) WHERE id > 1" to false),
      "star of a subquery before ORDER BY" to ("SELECT * FROM (SELECT id FROM item) ORDER BY id" to false),
      "star of a lowercase subquery" to ("select * from (select id from item) union select 2" to false),
      "star of a WITH subquery" to ("SELECT * FROM (WITH t AS (SELECT 1) SELECT 2) LIMIT 1" to false),
      "star of a nested subquery" to ("SELECT * FROM (SELECT (1 + (2)) AS v FROM item);" to false),
      "star of a subquery with its own star" to ("SELECT * FROM (SELECT * FROM item)" to true),
      "star of a subquery joined with a table" to ("SELECT * FROM (SELECT id FROM item) JOIN other" to true),
      "star of an aliased subquery" to ("SELECT * FROM (SELECT id FROM item) AS s" to true),
      "star of a subquery listed with a table" to ("SELECT * FROM (SELECT id FROM item), other" to true),
      "qualified star with a later join" to ("SELECT t.* FROM (SELECT id FROM item) s JOIN t" to true),
      "parenthesis in a string" to ("SELECT * FROM (SELECT ') UNION' FROM item) JOIN t" to true),
      "parenthesis in a quoted name" to ("""SELECT * FROM (SELECT ") UNION" FROM item) JOIN t""" to true),
      "parenthesis in a block comment" to ("SELECT * FROM (SELECT id /* ) UNION */ FROM item) JOIN t" to true),
      "parenthesis in a line comment" to ("SELECT * FROM (SELECT id -- ) UNION\nFROM item) JOIN t" to true),
      "nested parentheses before a join" to ("SELECT * FROM (SELECT (1 + (2)) FROM item) JOIN t" to true)
    )

    assertThat(cases.mapValues { (_, case) -> hasSchemaDependentColumns(case.first) })
      .isEqualTo(cases.mapValues { (_, case) -> case.second })
  }
}
