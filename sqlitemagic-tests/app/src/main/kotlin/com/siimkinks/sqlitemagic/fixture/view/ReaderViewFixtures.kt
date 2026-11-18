package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.ReaderAllNullableInnerViewTable.Companion.READER_ALL_NULLABLE_INNER_VIEW
import com.siimkinks.sqlitemagic.ReaderAllNullableNoIdProjectionTable.Companion.READER_ALL_NULLABLE_NO_ID_PROJECTION
import com.siimkinks.sqlitemagic.ReaderNestedNameViewTable.Companion.READER_NESTED_NAME_VIEW
import com.siimkinks.sqlitemagic.ReaderNoIdProjectionTable.Companion.READER_NO_ID_PROJECTION
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReaderRelationshipBookTable.Companion.READER_RELATIONSHIP_BOOK
import com.siimkinks.sqlitemagic.ReaderRelationshipTrailingScalarViewTable.Companion.READER_RELATIONSHIP_TRAILING_SCALAR_VIEW
import com.siimkinks.sqlitemagic.ReaderRepeatedNestedViewTable.Companion.READER_REPEATED_NESTED_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

private val READER_QUERY = SELECT FROM SIMPLE_MUTABLE_ENTITY

internal val READER_RELATIONSHIP_QUERY = SELECT FROM (READER_RELATIONSHIP_BOOK AS "book")

@Table
data class ReaderNoIdProjection(
  val code: String,
  val count: Int
)

@Table
data class ReaderAllNullableNoIdProjection(
  val code: String?,
  val count: Int?
)

@View
data class ReaderRequiredNullableTableView(
  @ViewColumn("row") val row: ReaderAllNullableNoIdProjection
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_ALL_NULLABLE_NO_ID_PROJECTION AS "row")
  }
}

@View
data class ReaderAllNullableInnerView(
  @ViewColumn("code") val code: String?,
  @ViewColumn("count") val count: Int?
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

@View
data class ReaderRequiredNullableNestedView(
  @ViewColumn("inner") val inner: ReaderAllNullableInnerView
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_ALL_NULLABLE_INNER_VIEW AS "inner")
  }
}

@View
data class ReaderReorderedNullableNestedView(
  @ViewColumn("inner") val inner: ReaderAllNullableInnerView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_ALL_NULLABLE_INNER_VIEW AS "inner")
  }
}

@View
data class ReaderNullableNoIdView(
  @ViewColumn("row") val row: ReaderNoIdProjection?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_NO_ID_PROJECTION AS "row")
  }
}

@Table
data class ReaderRelationshipAuthor(
  @Id val id: Long,
  val name: String
)

@Table
data class ReaderRelationshipBook(
  @Id val id: Long,
  @Column(handleRecursively = true) val author: ReaderRelationshipAuthor
)

@View
data class ReaderRelationshipTrailingScalarView(
  @ViewColumn("book") val book: ReaderRelationshipBook,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_RELATIONSHIP_QUERY
  }
}

@View
data class ReaderNullableRelationshipTrailingScalarView(
  @ViewColumn("book") val book: ReaderRelationshipBook?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_RELATIONSHIP_QUERY
  }
}

@View
data class ReaderOuterJoinedBlockView(
  @ViewColumn("author") val author: ReaderRelationshipAuthor,
  @ViewColumn("row") val row: ReaderNoIdProjection?
) {
  companion object {
    private val author = READER_RELATIONSHIP_AUTHOR AS "author"
    private val row = READER_NO_ID_PROJECTION AS "row"

    @ViewQuery
    val query = SELECT
      .COLUMNS(author, row.nullable())
      .FROM(author)
      .LEFT_JOIN(row)
      .ON(author.NAME IS row.CODE)
  }
}

@View
data class ReaderOuterJoinedRelationshipBlockView(
  @ViewColumn("owner") val owner: ReaderRelationshipAuthor,
  @ViewColumn("book") val book: ReaderRelationshipBook?
) {
  companion object {
    private val owner = READER_RELATIONSHIP_AUTHOR AS "owner"
    private val book = READER_RELATIONSHIP_BOOK AS "book"

    @ViewQuery
    val query = SELECT
      .COLUMNS(owner, book.nullable())
      .FROM(owner)
      .LEFT_JOIN(book)
      .ON(book.AUTHOR IS owner.ID)
  }
}

data class ReaderEmbeddedContact(
  val city: String,
  val zip: Int?
)

@View
data class ReaderNestedNameView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

data class ReaderEmbeddedNestedName(
  val nested: ReaderNestedNameView,
  val tail: String
)

@View
data class ReaderEmbeddedViewLeafView(
  @Embedded(prefix = "details_") val details: ReaderEmbeddedNestedName
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_NESTED_NAME_VIEW AS "details_nested")
  }
}

@View
data class ReaderEmbeddedNestedView(
  @Embedded(prefix = "contact_") val contact: ReaderEmbeddedContact?,
  @ViewColumn("inner") val inner: ReaderNestedNameView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = SELECT FROM (READER_NESTED_NAME_VIEW AS "inner")
  }
}

@View
data class ReaderRepeatedNestedView(
  @ViewColumn("left") val left: ReaderNestedNameView?,
  @ViewColumn("right") val right: ReaderNestedNameView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    private val left = READER_NESTED_NAME_VIEW AS "left"
    private val right = READER_NESTED_NAME_VIEW AS "right"

    @ViewQuery
    val query = SELECT
      .COLUMNS(left.nullable(), right.nullable())
      .FROM(left)
      .CROSS_JOIN(right)
  }
}

@View
data class ReaderNestedSpansView(
  @ViewColumn("relationship") val relationship: ReaderRelationshipTrailingScalarView,
  @ViewColumn("repeated") val repeated: ReaderRepeatedNestedView
) {
  companion object {
    private val relationship = READER_RELATIONSHIP_TRAILING_SCALAR_VIEW AS "relationship"
    private val repeated = READER_REPEATED_NESTED_VIEW AS "repeated"

    @ViewQuery
    val query = SELECT
      .COLUMNS(relationship, repeated)
      .FROM(relationship)
      .CROSS_JOIN(repeated)
  }
}

@View
class ReaderMutableDefaultsView {
  @Embedded(prefix = "contact_")
  var contact: ReaderEmbeddedContact? = ReaderEmbeddedContact(city = "seed", zip = 1)

  @ViewColumn("note")
  var note: String? = "seed"

  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

data class ReaderEmail(val value: String)

@ObjectToDbValue
fun readerEmailToString(value: ReaderEmail): String = value.value

@DbValueToObject
fun readerStringToEmail(value: String): ReaderEmail = ReaderEmail(value)

@View
data class ReaderTransformedScalarView(
  @ViewColumn("email") val email: ReaderEmail
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}
