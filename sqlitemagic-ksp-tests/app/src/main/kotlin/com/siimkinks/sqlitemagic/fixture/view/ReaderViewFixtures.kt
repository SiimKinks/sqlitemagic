package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Select.SelectN
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
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity

private val READER_QUERY: CompiledSelect<SimpleMutableEntity, SelectN> =
  Select
    .all()
    .from(SIMPLE_MUTABLE_ENTITY)
    .compile()

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
    val query = READER_QUERY
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
    val query = READER_QUERY
  }
}

@View
data class ReaderReorderedNullableNestedView(
  @ViewColumn("inner") val inner: ReaderAllNullableInnerView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

@View
data class ReaderNullableNoIdView(
  @ViewColumn("row") val row: ReaderNoIdProjection?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
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
    val query = READER_QUERY
  }
}

@View
data class ReaderNullableRelationshipTrailingScalarView(
  @ViewColumn("book") val book: ReaderRelationshipBook?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
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

@View
data class ReaderEmbeddedNestedView(
  @Embedded(prefix = "contact_") val contact: ReaderEmbeddedContact?,
  @ViewColumn("inner") val inner: ReaderNestedNameView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

@View
data class ReaderRepeatedNestedView(
  @ViewColumn("left") val left: ReaderNestedNameView?,
  @ViewColumn("right") val right: ReaderNestedNameView?,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
  }
}

@View
data class ReaderNestedSpansView(
  @ViewColumn("relationship") val relationship: ReaderRelationshipTrailingScalarView,
  @ViewColumn("repeated") val repeated: ReaderRepeatedNestedView
) {
  companion object {
    @ViewQuery
    val query = READER_QUERY
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
