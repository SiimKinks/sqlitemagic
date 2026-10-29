@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.TableRelationship
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding

internal data class Publisher(
  val id: Long,
  val name: String? = null
)

internal data class Author(
  val id: Long,
  val name: String,
  val publisher: Publisher?
)

internal data class Book(
  val id: Long,
  val title: String,
  val author: Author
)

internal data class Series(
  val id: Long,
  val name: String,
  val firstBook: Book,
  val lastBook: Book?
)

internal class PublisherTable(alias: String? = null) : Table<Publisher>(
  name = "publisher",
  alias = alias
) {
  val ID = UniqueColumn<Publisher, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val NAME = Column<Publisher, String, SqlNullable, String?, String?, SqlNullable, String?>(
    table = this,
    name = "name",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = Cursor::getString
  )

  override val entityFields = listOf(ID, NAME)

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Publisher(
    id = ID.selectRead(cursor, startIndex),
    name = NAME.selectRead(cursor, startIndex + 1)
  )

  override val entityProperties = listOf(
    EntityProperty(
      path = "Publisher.id",
      required = true
    ),
    EntityProperty(
      path = "Publisher.name",
      required = false
    )
  )

  override fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = Publisher(
    id = cursor.getLong(positions[0]),
    name = positions[1].takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
  )

  override infix fun AS(alias: String) = PublisherTable(alias)
}

internal val PUBLISHER = PublisherTable()

internal class AuthorTable(alias: String? = null) : Table<Author>(
  name = "author",
  alias = alias
) {
  val ID = UniqueColumn<Author, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val NAME = Column<Author, String, Required, String, String, Required, String>(
    table = this,
    name = "name",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val PUBLISHER = Column(
    table = this,
    name = "publisher",
    nullness = SqlNullable,
    parameterEncoding = LONG.nullBypassParameterEncoding(),
    selectedRead = nullableRead(Cursor::getLong)
  )

  override val entityFields = listOf(ID, NAME, PUBLISHER)

  override val relationships get() = AUTHOR_RELATIONSHIPS

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Author(
    id = ID.selectRead(cursor, startIndex),
    name = NAME.selectRead(cursor, startIndex + 1),
    publisher = when {
      cursor.isNull(startIndex + 2) -> null
      else -> related?.read(cursor, 0) as Publisher? ?: Publisher(id = cursor.getLong(startIndex + 2))
    }
  )

  override val entityProperties = listOf(
    EntityProperty(
      path = "Author.id",
      required = true
    ),
    EntityProperty(
      path = "Author.name",
      required = true
    ),
    EntityProperty(
      path = "Author.publisher",
      required = false
    )
  )

  override fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = Author(
    id = cursor.getLong(positions[0]),
    name = cursor.getString(positions[1]),
    publisher = related?.readSelected(cursor, 0) as Publisher? ?: when {
      positions[2] < 0 || cursor.isNull(positions[2]) -> null
      else -> Publisher(id = cursor.getLong(positions[2]))
    }
  )

  override infix fun AS(alias: String) = AuthorTable(alias)
}

private val AUTHOR_RELATIONSHIPS = listOf(
  TableRelationship(
    columnName = "publisher",
    target = { PUBLISHER },
    targetIdColumnName = "id",
    nullable = true,
    read = DEEP
  )
)

internal val AUTHOR = AuthorTable()

internal class BookTable(alias: String? = null) : Table<Book>(
  name = "book",
  alias = alias
) {
  val ID = UniqueColumn<Book, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val TITLE = Column<Book, String, Required, String, String, Required, String>(
    table = this,
    name = "title",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val AUTHOR = Column<Book, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "author",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )

  override val entityFields = listOf(ID, TITLE, AUTHOR)

  override val relationships get() = BOOK_RELATIONSHIPS

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Book(
    id = ID.selectRead(cursor, startIndex),
    title = TITLE.selectRead(cursor, startIndex + 1),
    author = checkNotNull(related).read(cursor, 0) as Author
  )

  override val entityProperties = listOf(
    EntityProperty(
      path = "Book.id",
      required = true
    ),
    EntityProperty(
      path = "Book.title",
      required = true
    ),
    EntityProperty(
      path = "Book.author",
      required = true
    )
  )

  override fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = Book(
    id = cursor.getLong(positions[0]),
    title = cursor.getString(positions[1]),
    author = checkNotNull(related).readSelected(cursor, 0) as Author
  )

  override infix fun AS(alias: String) = BookTable(alias)
}

private val BOOK_RELATIONSHIPS = listOf(
  TableRelationship(
    columnName = "author",
    target = { AUTHOR },
    targetIdColumnName = "id",
    nullable = false,
    read = ALWAYS
  )
)

internal val BOOK = BookTable()

internal class SeriesTable(alias: String? = null) : Table<Series>(
  name = "series",
  alias = alias
) {
  val ID = UniqueColumn<Series, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val NAME = Column<Series, String, Required, String, String, Required, String>(
    table = this,
    name = "name",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val FIRST_BOOK = Column<Series, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "first_book",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val LAST_BOOK = Column(
    table = this,
    name = "last_book",
    nullness = SqlNullable,
    parameterEncoding = LONG.nullBypassParameterEncoding(),
    selectedRead = nullableRead(Cursor::getLong)
  )

  override val entityFields = listOf(ID, NAME, FIRST_BOOK, LAST_BOOK)

  override val relationships get() = SERIES_RELATIONSHIPS

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Series(
    id = ID.selectRead(cursor, startIndex),
    name = NAME.selectRead(cursor, startIndex + 1),
    firstBook = checkNotNull(related).read(cursor, 0) as Book,
    lastBook = when {
      cursor.isNull(startIndex + 3) -> null
      else -> related.read(cursor, 1) as Book
    }
  )

  override infix fun AS(alias: String) = SeriesTable(alias)
}

private val SERIES_RELATIONSHIPS = listOf(
  TableRelationship(
    columnName = "first_book",
    target = { BOOK },
    targetIdColumnName = "id",
    nullable = false,
    read = ALWAYS
  ),
  TableRelationship(
    columnName = "last_book",
    target = { BOOK },
    targetIdColumnName = "id",
    nullable = true,
    read = ALWAYS
  )
)

internal val SERIES = SeriesTable()

internal val tor = Publisher(
  id = 1L,
  name = "Tor"
)
internal val unnamedPublisher = Publisher(id = 2L)
internal val adaAuthor = Author(
  id = 1L,
  name = "Ada",
  publisher = tor
)
internal val graceAuthor = Author(
  id = 2L,
  name = "Grace",
  publisher = null
)
internal val linusAuthor = Author(
  id = 3L,
  name = "Linus",
  publisher = unnamedPublisher
)

internal fun books(shallow: Boolean) = listOf(adaAuthor, graceAuthor, linusAuthor).mapIndexed { index, author ->
  Book(
    id = index + 1L,
    title = listOf("Dune", "Emma", "Kernel")[index],
    author = when {
      shallow -> author.copy(publisher = author.publisher?.copy(name = null))
      else -> author
    }
  )
}

internal fun libraryDatabase() = personDatabase().apply {
  execSQL("CREATE TABLE publisher (id INTEGER PRIMARY KEY, name TEXT)")
  execSQL("INSERT INTO publisher VALUES (1, 'Tor'), (2, NULL)")
  execSQL("CREATE TABLE author (id INTEGER PRIMARY KEY, name TEXT NOT NULL, publisher INTEGER)")
  execSQL("INSERT INTO author VALUES (1, 'Ada', 1), (2, 'Grace', NULL), (3, 'Linus', 2)")
  execSQL("CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT NOT NULL, author INTEGER NOT NULL)")
  execSQL("INSERT INTO book VALUES (1, 'Dune', 1), (2, 'Emma', 2), (3, 'Kernel', 3)")
  execSQL(
    "CREATE TABLE series (id INTEGER PRIMARY KEY, name TEXT NOT NULL, first_book INTEGER NOT NULL, last_book INTEGER)"
  )
  execSQL("INSERT INTO series VALUES (1, 'Classics', 1, 2), (2, 'Solo', 3, NULL)")
}

internal fun libraryDatabaseWithNotes() = libraryDatabase().apply {
  execSQL("INSERT INTO book VALUES (4, 'Notes', 1)")
}
