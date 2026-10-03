package com.siimkinks.sqlitemagic.view

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.cursorOf
import com.siimkinks.sqlitemagic.fixture.view.ReaderAllNullableInnerView
import com.siimkinks.sqlitemagic.fixture.view.ReaderAllNullableNoIdProjection
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmail
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedContact
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedNestedName
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmbeddedViewLeafView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNestedNameView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNestedSpansView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNoIdProjection
import com.siimkinks.sqlitemagic.fixture.view.ReaderNullableNoIdView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNullableRelationshipTrailingScalarView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipTrailingScalarView
import com.siimkinks.sqlitemagic.fixture.view.ReaderReorderedNullableNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRepeatedNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRequiredNullableNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRequiredNullableTableView
import com.siimkinks.sqlitemagic.fixture.view.ReaderTransformedScalarView
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderEmbeddedNestedView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderEmbeddedViewLeafView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderMutableDefaultsView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderNestedNameView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderNestedSpansView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderNullableNoIdView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderNullableRelationshipTrailingScalarView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderRelationshipTrailingScalarView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderReorderedNullableNestedView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderRepeatedNestedView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderRequiredNullableNestedView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderRequiredNullableTableView_Dao
import com.siimkinks.sqlitemagic.fixture.view.SqliteMagic_ReaderTransformedScalarView_Dao
import com.siimkinks.sqlitemagic.internal.MutableInt
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

internal class GeneratedViewReaderTest {
  @Test
  fun `reads nullable no-id projections at a nonzero offset`() {
    val allNullOffset = MutableInt().apply { value = 2 }
    val allNull = SqliteMagic_ReaderNullableNoIdView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null, null, null, "tail"),
      columnOffset = allNullOffset
    )
    assertThat(allNull).isEqualTo(
      ReaderNullableNoIdView(
        row = null,
        tail = "tail"
      )
    )
    assertThat(allNullOffset.value).isEqualTo(5)

    val presentOffset = MutableInt().apply { value = 2 }
    val present = SqliteMagic_ReaderNullableNoIdView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null, "ABC", 7, "next"),
      columnOffset = presentOffset
    )
    assertThat(present).isEqualTo(
      ReaderNullableNoIdView(
        row = ReaderNoIdProjection(
          code = "ABC",
          count = 7
        ),
        tail = "next"
      )
    )
    assertThat(presentOffset.value).isEqualTo(5)

    val partialOffset = MutableInt().apply { value = 2 }
    assertThrows<SQLException> {
      SqliteMagic_ReaderNullableNoIdView_Dao.shallowObjectFromCursorPosition(
        cursor = cursorOf(null, null, null, 7, "tail"),
        columnOffset = partialOffset
      )
    }
    assertThat(partialOffset.value).isEqualTo(5)

    val columns = MutableObjectIntMap<String>().apply {
      this["reader.tail"] = 4
    }
    val missingProjectionCursor = cursorOf(null, null, null, null, "tail")
    assertThrows<SQLException> {
      SqliteMagic_ReaderNullableNoIdView_Dao.shallowObjectFromCursorPosition(
        cursor = missingProjectionCursor,
        columns = columns,
        tableGraphNodeNames = null,
        nodeName = "reader"
      )
    }
    verifyNoInteractions(missingProjectionCursor)
  }

  @Test
  fun `non-null numeric table leaf rejects SQL null inside present projection`() {
    val cursor = cursorOf("ABC", null, "tail")

    assertThrows<SQLException> {
      SqliteMagic_ReaderNullableNoIdView_Dao.shallowObjectFromCursorPosition(
        cursor = cursor
      )
    }
  }

  @Test
  fun `uses relationship width before the trailing scalar`() {
    val offset = MutableInt()
    val view = SqliteMagic_ReaderRelationshipTrailingScalarView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(1L, 2L, 2L, "Ada", "tail"),
      columnOffset = offset
    )

    assertThat(view).isEqualTo(
      ReaderRelationshipTrailingScalarView(
        book = ReaderRelationshipBook(
          id = 1L,
          author = ReaderRelationshipAuthor(
            id = 2L,
            name = "Ada"
          )
        ),
        tail = "tail"
      )
    )
    assertThat(offset.value).isEqualTo(5)
  }

  @Test
  fun `selected relationship projection reads a scalar between table spans`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.book"] = 0
      this["view.tail"] = 2
      this["view.author"] = 3
    }
    val graph = MutableScatterMap<String, String>().apply {
      this["view."] = "book"
      this["view.author"] = "author"
    }

    val view = SqliteMagic_ReaderRelationshipTrailingScalarView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(1L, 2L, "tail", 2L, "Ada"),
      columns = columns,
      tableGraphNodeNames = graph,
      nodeName = "view"
    )

    assertThat(view).isEqualTo(
      ReaderRelationshipTrailingScalarView(
        book = ReaderRelationshipBook(
          id = 1L,
          author = ReaderRelationshipAuthor(
            id = 2L,
            name = "Ada"
          )
        ),
        tail = "tail"
      )
    )
  }

  @Test
  fun `nullable relationship presence excludes interleaved scalar tail`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.book"] = 0
      this["view.tail"] = 2
      this["view.author"] = 3
    }
    val graph = MutableScatterMap<String, String>().apply {
      this["view."] = "book"
      this["view.author"] = "author"
    }

    val view = SqliteMagic_ReaderNullableRelationshipTrailingScalarView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null, "tail", null, null),
      columns = columns,
      tableGraphNodeNames = graph,
      nodeName = "view"
    )

    assertThat(view).isEqualTo(
      ReaderNullableRelationshipTrailingScalarView(
        book = null,
        tail = "tail"
      )
    )
  }

  @Test
  fun `missing relationship descendant fails before nullable projection absence`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.book"] = 0
      this["view.tail"] = 2
      this["view.author.id"] = 3
    }
    val graph = MutableScatterMap<String, String>().apply {
      this["view."] = "book"
      this["view.author"] = "author"
    }
    val cursor = cursorOf(null, null, "tail", null, null)

    assertThrows<SQLException> {
      SqliteMagic_ReaderNullableRelationshipTrailingScalarView_Dao.shallowObjectFromCursorPosition(
        cursor = cursor,
        columns = columns,
        tableGraphNodeNames = graph,
        nodeName = "view"
      )
    }

    verifyNoInteractions(cursor)
  }

  @Test
  fun `selected nested view resolves reordered mapped leaves for presence and construction`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.tail"] = 0
      this["view.inner.count"] = 1
      this["view.inner.code"] = 3
    }
    val present = SqliteMagic_ReaderReorderedNullableNestedView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf("tail", 7, "unmapped", null),
      columns = columns,
      tableGraphNodeNames = null,
      nodeName = "view"
    )
    assertThat(present).isEqualTo(
      ReaderReorderedNullableNestedView(
        inner = ReaderAllNullableInnerView(
          code = null,
          count = 7
        ),
        tail = "tail"
      )
    )

    val absent = SqliteMagic_ReaderReorderedNullableNestedView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf("tail", null, "unmapped", null),
      columns = columns,
      tableGraphNodeNames = null,
      nodeName = "view"
    )
    assertThat(absent).isEqualTo(
      ReaderReorderedNullableNestedView(
        inner = null,
        tail = "tail"
      )
    )
  }

  @Test
  fun `missing nullable nested descendant fails before testing absence`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.tail"] = 0
      this["view.inner.code"] = 3
    }
    val cursor = cursorOf("tail", null, "unmapped", null)

    assertThrows<SQLException> {
      SqliteMagic_ReaderReorderedNullableNestedView_Dao.shallowObjectFromCursorPosition(
        cursor = cursor,
        columns = columns,
        tableGraphNodeNames = null,
        nodeName = "view"
      )
    }

    verifyNoInteractions(cursor)
  }

  @Test
  fun `required projections with nullable leaves construct objects from all null columns`() {
    val tableOffset = MutableInt()
    val tableView = SqliteMagic_ReaderRequiredNullableTableView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null),
      columnOffset = tableOffset
    )
    assertThat(tableView).isEqualTo(
      ReaderRequiredNullableTableView(
        row = ReaderAllNullableNoIdProjection(
          code = null,
          count = null
        )
      )
    )
    assertThat(tableOffset.value).isEqualTo(2)

    val nestedOffset = MutableInt()
    val nestedView = SqliteMagic_ReaderRequiredNullableNestedView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null),
      columnOffset = nestedOffset
    )
    assertThat(nestedView).isEqualTo(
      ReaderRequiredNullableNestedView(
        inner = ReaderAllNullableInnerView(
          code = null,
          count = null
        )
      )
    )
    assertThat(nestedOffset.value).isEqualTo(2)
  }

  @Test
  fun `selected full view span reads at zero and nonzero offsets without leaf positions`() {
    data class Case(val label: String, val offset: Int, val values: List<Any?>)

    val cases = listOf(
      Case(
        label = "zero offset",
        offset = 0,
        values = listOf("Ada", "Grace", "tail")
      ),
      Case(
        label = "nonzero offset",
        offset = 2,
        values = listOf("ignored", 99, "Ada", "Grace", "tail")
      )
    )
    val expected = ReaderRepeatedNestedView(
      left = ReaderNestedNameView(name = "Ada"),
      right = ReaderNestedNameView(name = "Grace"),
      tail = "tail"
    )

    cases.forEach { case ->
      val columns = MutableObjectIntMap<String>().apply { this["view"] = case.offset }
      val actual = SqliteMagic_ReaderRepeatedNestedView_Dao.shallowObjectFromCursorPosition(
        cursor = cursorOf(*case.values.toTypedArray()),
        columns = columns,
        tableGraphNodeNames = null,
        nodeName = "view"
      )
      assertWithMessage(case.label).that(actual).isEqualTo(expected)
    }
  }

  @Test
  fun `selected nested full spans reconstruct relationship and view descendants`() {
    val columns = MutableObjectIntMap<String>().apply {
      this["view.relationship"] = 2
      this["view.repeated"] = 7
    }

    val view = SqliteMagic_ReaderNestedSpansView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf("prefix", 99, 1L, 2L, 2L, "Ada", "inner-tail", "Left", "Right", "repeat-tail"),
      columns = columns,
      tableGraphNodeNames = null,
      nodeName = "view"
    )

    assertThat(view).isEqualTo(
      ReaderNestedSpansView(
        relationship = ReaderRelationshipTrailingScalarView(
          book = ReaderRelationshipBook(
            id = 1L,
            author = ReaderRelationshipAuthor(
              id = 2L,
              name = "Ada"
            )
          ),
          tail = "inner-tail"
        ),
        repeated = ReaderRepeatedNestedView(
          left = ReaderNestedNameView(name = "Left"),
          right = ReaderNestedNameView(name = "Right"),
          tail = "repeat-tail"
        )
      )
    )
  }

  @Test
  fun `unrelated bare definition key cannot satisfy selected required view column`() {
    val columns = MutableObjectIntMap<String>().apply { this["name"] = 0 }
    val cursor = cursorOf("Ada")

    assertThrows<SQLException> {
      SqliteMagic_ReaderNestedNameView_Dao.shallowObjectFromCursorPosition(
        cursor = cursor,
        columns = columns,
        tableGraphNodeNames = null,
        nodeName = "view"
      )
    }

    verifyNoInteractions(cursor)
  }

  @Test
  fun `reads a complete view leaf inside an embedded container before its trailing scalar`() {
    val offset = MutableInt()
    offset.value = 1
    val view = SqliteMagic_ReaderEmbeddedViewLeafView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf("ignored", "Ada", "tail"),
      columnOffset = offset
    )

    assertThat(view).isEqualTo(
      ReaderEmbeddedViewLeafView(
        details = ReaderEmbeddedNestedName(
          nested = ReaderNestedNameView(name = "Ada"),
          tail = "tail"
        )
      )
    )
    assertThat(offset.value)
      .isEqualTo(3)
  }

  @Test
  fun `reconstructs embedded and nested view boundaries`() {
    val embeddedOffset = MutableInt()
    val embedded = SqliteMagic_ReaderEmbeddedNestedView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf("Tallinn", null, "Ada", "next"),
      columnOffset = embeddedOffset
    )
    assertThat(embedded).isEqualTo(
      ReaderEmbeddedNestedView(
        contact = ReaderEmbeddedContact(
          city = "Tallinn",
          zip = null
        ),
        inner = ReaderNestedNameView(name = "Ada"),
        tail = "next"
      )
    )
    assertThat(embeddedOffset.value).isEqualTo(4)

    val repeatedOffset = MutableInt()
    val repeated = SqliteMagic_ReaderRepeatedNestedView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, "Grace", "tail"),
      columnOffset = repeatedOffset
    )
    assertThat(repeated).isEqualTo(
      ReaderRepeatedNestedView(
        left = null,
        right = ReaderNestedNameView(name = "Grace"),
        tail = "tail"
      )
    )
    assertThat(repeatedOffset.value).isEqualTo(3)
  }

  @Test
  fun `preserves mutable defaults for null cursor columns`() {
    val offset = MutableInt()
    val view = SqliteMagic_ReaderMutableDefaultsView_Dao.shallowObjectFromCursorPosition(
      cursor = cursorOf(null, null, null),
      columnOffset = offset
    )

    assertThat(view.contact).isEqualTo(
      ReaderEmbeddedContact(
        city = "seed",
        zip = 1
      )
    )
    assertThat(view.note).isEqualTo("seed")
    assertThat(offset.value).isEqualTo(3)
  }

  @Test
  fun `reads transformed scalar leaves`() {
    val offset = MutableInt().apply { value = 2 }
    val cursor = cursorOf("prefix", 99, "ada@example.com")
    val view = SqliteMagic_ReaderTransformedScalarView_Dao.shallowObjectFromCursorPosition(
      cursor = cursor,
      columnOffset = offset
    )

    assertThat(view).isEqualTo(
      ReaderTransformedScalarView(
        email = ReaderEmail(value = "ada@example.com")
      )
    )
    assertThat(offset.value).isEqualTo(3)
    verify(cursor).isNull(2)
    verify(cursor).getString(2)
  }

  @Test
  fun `selected transformed scalar reads its reordered getter once`() {
    val cursor = cursorOf("prefix", 99, "ada@example.com")
    val columns = MutableObjectIntMap<String>().apply { this["view.email"] = 2 }
    val view = SqliteMagic_ReaderTransformedScalarView_Dao.shallowObjectFromCursorPosition(
      cursor = cursor,
      columns = columns,
      tableGraphNodeNames = null,
      nodeName = "view"
    )

    assertThat(view).isEqualTo(
      ReaderTransformedScalarView(
        email = ReaderEmail(value = "ada@example.com")
      )
    )
    verify(cursor).isNull(2)
    verify(cursor).getString(2)
  }
}
