@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.view

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ReaderEmbeddedNestedViewTable.Companion.READER_EMBEDDED_NESTED_VIEW
import com.siimkinks.sqlitemagic.ReaderEmbeddedViewLeafViewTable.Companion.READER_EMBEDDED_VIEW_LEAF_VIEW
import com.siimkinks.sqlitemagic.ReaderNullableNoIdViewTable.Companion.READER_NULLABLE_NO_ID_VIEW
import com.siimkinks.sqlitemagic.ReaderNullableRelationshipTrailingScalarViewTable.Companion.READER_NULLABLE_RELATIONSHIP_TRAILING_SCALAR_VIEW
import com.siimkinks.sqlitemagic.ReaderReorderedNullableNestedViewTable.Companion.READER_REORDERED_NULLABLE_NESTED_VIEW
import com.siimkinks.sqlitemagic.ReaderRequiredNullableNestedViewTable.Companion.READER_REQUIRED_NULLABLE_NESTED_VIEW
import com.siimkinks.sqlitemagic.ReaderRequiredNullableTableViewTable.Companion.READER_REQUIRED_NULLABLE_TABLE_VIEW
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderAllNullableInnerView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderEmbeddedNestedView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderEmbeddedViewLeafView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderMutableDefaultsView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderNullableNoIdView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderNullableRelationshipTrailingScalarView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderReorderedNullableNestedView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderRequiredNullableNestedView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderRequiredNullableTableView_Dao
import com.siimkinks.sqlitemagic.SqliteMagic_ReaderTransformedScalarView_Dao
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.cursorOf
import com.siimkinks.sqlitemagic.fixture.view.ReaderAllNullableInnerView
import com.siimkinks.sqlitemagic.fixture.view.ReaderAllNullableNoIdProjection
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmail
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedContact
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedNestedName
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedViewLeafView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNestedNameView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNoIdProjection
import com.siimkinks.sqlitemagic.fixture.view.ReaderNullableNoIdView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNullableRelationshipTrailingScalarView
import com.siimkinks.sqlitemagic.fixture.view.ReaderReorderedNullableNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRequiredNullableNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRequiredNullableTableView
import com.siimkinks.sqlitemagic.fixture.view.ReaderTransformedScalarView
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

internal class GeneratedViewReaderTest {
  @Test
  fun `reads scalar columns from a start column`() {
    assertThat(
      listOf(
        SqliteMagic_ReaderAllNullableInnerView_Dao.objectFromCursorPosition(
          cursor = cursorOf("skipped", "code", 3),
          startIndex = 1
        ),
        SqliteMagic_ReaderAllNullableInnerView_Dao.objectFromCursorPosition(
          cursor = cursorOf(null, null),
          startIndex = 0
        )
      )
    ).isEqualTo(
      listOf(
        ReaderAllNullableInnerView(
          code = "code",
          count = 3
        ),
        ReaderAllNullableInnerView(
          code = null,
          count = null
        )
      )
    )
  }

  @Test
  fun `reads required blocks whose leaves are all null`() {
    assertThat(
      listOf(
        SqliteMagic_ReaderRequiredNullableTableView_Dao.objectFromCursorPosition(
          cursor = cursorOf(null, null),
          startIndex = 0,
          related = blocksOf(READER_REQUIRED_NULLABLE_TABLE_VIEW)
        ),
        SqliteMagic_ReaderRequiredNullableNestedView_Dao.objectFromCursorPosition(
          cursor = cursorOf(null, null),
          startIndex = 0,
          related = blocksOf(READER_REQUIRED_NULLABLE_NESTED_VIEW)
        )
      )
    ).isEqualTo(
      listOf(
        ReaderRequiredNullableTableView(
          row = ReaderAllNullableNoIdProjection(
            code = null,
            count = null
          )
        ),
        ReaderRequiredNullableNestedView(
          inner = ReaderAllNullableInnerView(
            code = null,
            count = null
          )
        )
      )
    )
  }

  @Test
  fun `reads nullable blocks after the view's own columns`() {
    assertThat(
      listOf(
        SqliteMagic_ReaderReorderedNullableNestedView_Dao.objectFromCursorPosition(
          cursor = cursorOf("tail", "code", 3),
          startIndex = 0,
          related = blocksOf(READER_REORDERED_NULLABLE_NESTED_VIEW)
        ),
        SqliteMagic_ReaderNullableNoIdView_Dao.objectFromCursorPosition(
          cursor = cursorOf("tail", "code", 3),
          startIndex = 0,
          related = blocksOf(READER_NULLABLE_NO_ID_VIEW)
        ),
        SqliteMagic_ReaderNullableRelationshipTrailingScalarView_Dao.objectFromCursorPosition(
          cursor = cursorOf("tail", null, null, null, null),
          startIndex = 0,
          related = blocksOf(READER_NULLABLE_RELATIONSHIP_TRAILING_SCALAR_VIEW)
        )
      )
    ).isEqualTo(
      listOf(
        ReaderReorderedNullableNestedView(
          inner = ReaderAllNullableInnerView(
            code = "code",
            count = 3
          ),
          tail = "tail"
        ),
        ReaderNullableNoIdView(
          row = ReaderNoIdProjection(
            code = "code",
            count = 3
          ),
          tail = "tail"
        ),
        ReaderNullableRelationshipTrailingScalarView(
          book = null,
          tail = "tail"
        )
      )
    )
  }

  @Test
  fun `reads embedded values around nested views`() {
    assertThat(
      listOf(
        SqliteMagic_ReaderEmbeddedViewLeafView_Dao.objectFromCursorPosition(
          cursor = cursorOf("tail", "Ada"),
          startIndex = 0,
          related = blocksOf(READER_EMBEDDED_VIEW_LEAF_VIEW)
        ),
        SqliteMagic_ReaderEmbeddedNestedView_Dao.objectFromCursorPosition(
          cursor = cursorOf("Tallinn", null, "tail", "Ada"),
          startIndex = 0,
          related = blocksOf(READER_EMBEDDED_NESTED_VIEW)
        )
      )
    ).isEqualTo(
      listOf(
        ReaderEmbeddedViewLeafView(
          details = ReaderEmbeddedNestedName(
            nested = ReaderNestedNameView(name = "Ada"),
            tail = "tail"
          )
        ),
        ReaderEmbeddedNestedView(
          contact = ReaderEmbeddedContact(
            city = "Tallinn",
            zip = null
          ),
          inner = ReaderNestedNameView(name = "Ada"),
          tail = "tail"
        )
      )
    )
  }

  @Test
  fun `preserves mutable defaults for null cursor columns`() {
    val view = SqliteMagic_ReaderMutableDefaultsView_Dao.objectFromCursorPosition(
      cursor = cursorOf(null, null, null),
      startIndex = 0
    )

    assertThat(listOf(view.contact, view.note)).isEqualTo(
      listOf(
        ReaderEmbeddedContact(
          city = "seed",
          zip = 1
        ),
        "seed"
      )
    )
  }

  @Test
  fun `reads transformed scalar leaves`() {
    val cursor = cursorOf("prefix", 99, "ada@example.com")
    val view = SqliteMagic_ReaderTransformedScalarView_Dao.objectFromCursorPosition(
      cursor = cursor,
      startIndex = 2
    )

    assertThat(view).isEqualTo(ReaderTransformedScalarView(email = ReaderEmail(value = "ada@example.com")))
    verify(cursor).isNull(2)
    verify(cursor).getString(2)
  }

  /** Android's stubbed `SQLException` carries no message on the JVM; the generated text names the column. */
  @Test
  fun `a required column that reads NULL fails without decoding it`() {
    val cursor = cursorOf(null)

    assertThrows<SQLException> {
      SqliteMagic_ReaderTransformedScalarView_Dao.objectFromCursorPosition(
        cursor = cursor,
        startIndex = 0
      )
    }
    verify(cursor, never()).getString(0)
  }

  private fun blocksOf(view: Table<*>) = RelatedEntities(
    table = view,
    children = checkNotNull(view.viewDefinition?.blocks)
  )
}
