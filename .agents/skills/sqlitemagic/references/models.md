# Models

Tables, columns, IDs, relationships, embedded values, custom types, and what the processor generates from them.
Views are in [views.md](views.md).

## Contents

- [Tables](#tables)
- [IDs](#ids)
- [Columns, nullability and defaults](#columns-nullability-and-defaults)
- [Supported types and transformers](#supported-types-and-transformers)
- [Relationships](#relationships)
- [Embedded values](#embedded-values)
- [Unique columns and indexes](#unique-columns-and-indexes)
- [Generated code](#generated-code)

## Tables

```kotlin
@Table
data class Author(
  @Id val id: Long? = null,
  val name: String,
  val nickname: String? = null
)
```

- No base class or interface. A table model is either constructor-backed (every persisted property is a primary
  constructor parameter; data classes are the norm) or mutable (a no-argument constructor and `var` properties).
  Prefer immutable data classes.
- The table name is the class name in lower snake case (`BookReview` → `book_review`; nested classes prefix their
  enclosing names). `@Table("name")` overrides it, as `@Column("name")` overrides a column's.
- Every property with a backing field is persisted (`persistAll = true`); exclude one with `@IgnoreColumn`. With
  `@Table(persistAll = false)`, only `@Id`, `@Column` and `@Embedded` properties are persisted.
- `@Table(options = [TableOption.WITHOUT_ROWID])` stores rows by the declared `@Id`; use it for tables keyed by a
  non-integer ID that are read by key. `TableOption.TEMPORARY` creates the table per connection in the temporary
  database: it starts empty on every open and is not shared between connections.
- Keep models plain data. Behavior that needs the database (queries, statements) goes into the companion object or a
  repository, as functions returning queries.

## IDs

| Declaration | Meaning |
| --- | --- |
| `@Id val id: Long? = null` | SQLite assigns the row ID on insert (`INTEGER PRIMARY KEY AUTOINCREMENT`); `null` until saved. The usual choice |
| `@Id(autoIncrement = false) val id: Long` | the app assigns IDs, for example server IDs |
| `@Id val id: String` | text key (a UUID, a server key); never auto-generated |
| `@Id val id: AccountId` | a transformed type (a value class or wrapper); stored as its transformer's storage type |
| no `@Id` | an append-only or link table; insert plus explicit UPDATE/DELETE statements |

- Auto-increment applies by default when the ID stores as INTEGER and the generated code can convert the row ID back;
  explicit `true` demands it, explicit `false` turns it off. BLOB IDs are not allowed.
- An immutable model does not get its generated ID back: read it from the insert result
  (`(result as EntityInsertResult.Inserted).rowId`) or query the row again. A mutable model's `var id` is assigned after
  insert. Inside one `insert()`/`persist()` of an object graph, newly generated IDs of related objects are used for the
  foreign keys automatically.
- An ID that is a row-ID alias (`Long?` auto-increment) or a `WITHOUT ROWID` key is never NULL once stored, so
  `BOOK.ID` selects as `Long` although the property is `Long?`.
- A table without an ID but with a non-null single-column `@Unique` property supports update, persist and delete by
  that column: `entity.update().execute(ACCOUNT_SETTINGS.ACCOUNT_KEY)`
  ([entity-operations.md](entity-operations.md#tables-without-an-id)).

## Columns, nullability and defaults

- A non-null property is a `NOT NULL` column; a nullable property a nullable column. A column inside a nullable
  `@Embedded` value is nullable. Selected fields keep the property's type: `SELECT COLUMN BOOK.TITLE FROM BOOK` reads
  `List<String>`.
- Make a property nullable only when "no value" is a real state. Nullable columns cost NULL handling in every query
  (`IFNULL`, `.nullable()` lifts, `?:`), and the schema cannot protect the invariant.
- A `NOT NULL` column has an implicit SQL default: `''`, `0`, `0.0`, `X''` (blob), `X'00'` (Byte), `0` for `false`.
  `@Column(defaultValue = "…")` sets another, as SQL text: `"1"`, `"'unknown'"` (quote text). An expression default
  (`CURRENT_TIMESTAMP`) is valid; adding such a column rebuilds the table instead of `ADD COLUMN`.
  The SQL default matters for migrations (it fills the column for existing rows) and for raw INSERTs; Kotlin default
  values apply only when constructing objects, never in migrations. Keep the two consistent:

  ```kotlin
  @Column(defaultValue = "1")
  val pageSize: Int = 1
  ```

- A non-null transformed property needs a `defaultValue` its decoder accepts, such as
  `@Column(defaultValue = "'DRAFT'")` for `BookStatus`: the implicit default is the storage type's (such as `''` or
  `0`), and
  existing rows that a migration fills with it fail to decode (`BookStatus.valueOf("")` throws) on every read. The
  build warns when a migration does this.

- A required relationship, a `@Unique` column and a column of a unique index have no implicit default: adding one to
  an existing table makes the migration delete that table's rows. Add such a column as nullable when the table holds
  data that must survive. An explicit default also keeps the rows for a relationship (it must reference an existing
  row) or a unique-index column (it must keep the index unique); `@Unique` ignores `defaultValue`.
- The column name is the property name in snake case (`firstName` → `first_name`). Renaming a property renames the
  column, which the migration treats as drop plus add: keep the old name with `@Column("old_name")` when the data must
  survive.
- Columns named after SQL keywords (`order`, `group`) work; identifiers are always quoted.

## Supported types and transformers

Built-in column types, with their nullable forms:

| Kotlin | Storage |
| --- | --- |
| `String` | TEXT |
| `Long`, `Int`, `Short` | INTEGER |
| `Boolean` | INTEGER 0/1 (any non-zero reads `true`) |
| `Double`, `Float` | REAL (a NaN DSL value is rejected; a NaN property is stored as NULL, which NOT NULL rejects) |
| `ByteArray`, `Array<Byte>` | BLOB |
| `Byte` | BLOB of one byte |

Any other property type must be a `@Table` model (a relationship), an `@Embedded` value, or a type with a transformer.
Enums, dates, value classes and wrappers need a transformer: a pair of functions annotated `@ObjectToDbValue` and
`@DbValueToObject`, top-level or in an `object`/companion object, not private, suspending or extensions:

```kotlin
enum class BookStatus { DRAFT, PUBLISHED }

@ObjectToDbValue
fun bookStatusToDb(value: BookStatus): String = value.name

@DbValueToObject
fun bookStatusFromDb(value: String): BookStatus = BookStatus.valueOf(value)

@JvmInline
value class Isbn(val value: String)

@ObjectToDbValue
fun isbnToDb(value: Isbn): String = value.value

@DbValueToObject
fun isbnFromDb(value: String): Isbn = Isbn(value)
```

- One transformer pair per Kotlin type, used everywhere that type appears: columns, IDs, comparisons, `SET` values,
  parameters. The transformed type cannot itself be a built-in type or a `@Table` model.
- Store enums by `name` (stable under reordering) or by an explicit code, never by `ordinal`. Store instants as epoch
  milliseconds (`Long`), so they sort and compare in SQL.
- The storage type decides SQL behavior: ordering, `LIKE`, arithmetic and aggregates work on the stored value.
- A transformer is not called for SQL NULL of a nullable property unless its decoder parameter is nullable; write the
  pair non-null unless NULL needs a decoded value. An encoder must not return NULL for a non-null property: SQLite
  rejects it with `NOT NULL constraint failed`.
- For each transformed type the processor generates `SqlType.<TYPE_NAME>` (`SqlType.BOOK_STATUS`, `SqlType.ISBN`) and
  `asValue()`, used where an expression of that type is needed; an inline literal is
  `value.asLiteral(SqlType.BOOK_STATUS)` ([expressions.md](expressions.md#values)). Code in another package imports them
  (`import com.siimkinks.sqlitemagic.BOOK_STATUS`, `import com.siimkinks.sqlitemagic.asValue`).
- Transformers in another Gradle module are registered with `@Database(externalTransformers = [Holder::class])`
  ([setup.md](setup.md#models-in-several-modules)).

## Relationships

A property whose type is another `@Table` model is a relationship: the column stores the target's `@Id` (the foreign
key), and reads rebuild the object.

```kotlin
@Table
data class Book(
  @Id val id: Long? = null,
  val title: String,
  val pages: Int,
  @Column(onDeleteCascade = true)
  val author: Author,
  val editor: Author? = null,
  @Column(defaultValue = "'DRAFT'")
  val status: BookStatus = BookStatus.DRAFT
)
```

- `BOOK.AUTHOR` is the foreign-key field and compares with the target's ID type: `BOOK.AUTHOR IS authorId`,
  `BOOK.AUTHOR IS AUTHOR.ID`. The target must declare an `@Id`.
- `handleRecursively = true` (the default): `insert`, `update` and `persist` write the related object too, in one
  transaction, and reads load it (unless it can be built from its ID, see Reads below). With
  `@Column(handleRecursively = false)` only the foreign key is written and read; the target is rebuilt from its ID
  alone, so all its other constructor parameters must be nullable or have defaults.
  Use it for references to rows owned elsewhere (written by their own code), for large targets, and to break cycles.
- Relationship cycles, including self-references (`val parent: Category?`), require `handleRecursively = false` on at
  least one edge of the cycle; the processor reports the cycle and the fix.
- `onDeleteCascade = true` makes the column a foreign key, `REFERENCES … ON DELETE CASCADE` (deleting the author
  deletes their books), and turns on foreign-key enforcement for the database, in both `handleRecursively` modes. The
  referenced row must exist whenever the row is written; with `handleRecursively = false`, write it first. Other
  relationships store a plain ID without a foreign key, so deleting a referenced row leaves it dangling; a deep
  read of such a row reads the related object as ID-only where possible, `null` for a nullable property, and
  otherwise throws.
- Writing a parent writes its related objects as they are in memory: a related object built from its ID alone (a
  shallow read, see below) writes `null` or its Kotlin defaults over that row's other columns when the parent is
  updated or persisted. `persist().ignoreNullValues()` keeps only the `null` ones; read with `queryDeep()`, or make the
  relationship `handleRecursively = false` ([entity-operations.md](entity-operations.md#related-objects)).
- Reads: an entity query loads a related object in full, through an automatic `LEFT JOIN`, unless all its other
  constructor parameters are nullable or defaulted, in which case it is built from the stored ID alone. `queryDeep()`
  loads every recursively handled related object in full, at every depth, in the same single statement
  ([queries.md](queries.md#entity-queries)).

## Embedded values

`@Embedded` flattens a value object's properties into the table's columns, without a table or join:

```kotlin
data class Address(
  val street: String,
  val city: String
)

@Table
data class Library(
  @Id val id: Long? = null,
  val name: String,
  @Embedded(prefix = "home_")
  val home: Address,
  @Embedded(prefix = "branch_")
  val branch: Address?
)
```

- Columns are the embedded properties' names with every enclosing prefix, exactly as written: `home_street`,
  `branch_city`. Generated fields follow: `LIBRARY.HOME_STREET`, `LIBRARY.BRANCH_CITY`.
- A nullable embedded value stores NULL in all its columns and reads `null` when they are all NULL; its columns are
  nullable whatever the inner types.
- Embedded values nest; `@Column` and `@IgnoreColumn` work inside them. The `@Embedded` property itself takes none of
  `@Column`, `@Id`, `@IgnoreColumn`, `@Index` and `@Unique`, and `@Id` is not allowed inside an embedded value.
- Prefer `@Embedded` over a transformer that serializes several values into one TEXT column: the values stay
  queryable, sortable and indexable.

## Unique columns and indexes

- `@Unique` on a property adds a UNIQUE constraint. NULLs never conflict. A non-null unique column can identify rows for
  entity operations with `byColumn(…)` or `execute(column)`.
- `@Index` on a property indexes that column; `@Index(unique = true)` makes it a unique index.
- A composite index: `@Index("name")` on the class plus `@Column(belongsToIndex = "name")` on each member; an unnamed
  class-level `@Index` includes every column without a `belongsToIndex`. Its columns follow property declaration
  order.
- Index every `onDeleteCascade` column, as the first column of an index: deleting, replacing or re-keying a parent row
  looks up its children by that column, and without such an index each lookup scans the child table.
- Index what queries filter, join and sort by: foreign keys used in `WHERE`, columns in frequent `ORDER_BY` with
  `LIMIT`. Each index slows writes and takes space; do not index every column.

```kotlin
@Table
@Index("index_book_author_title", unique = true)
data class Book(
  @Id val id: Long? = null,
  @Column(belongsToIndex = "index_book_author_title", onDeleteCascade = true)
  val author: Author,
  @Column(belongsToIndex = "index_book_author_title")
  val title: String
)
```

## Generated code

Build after changing models; KSP generates, in package `com.siimkinks.sqlitemagic`:

| Generated | Example | Use |
| --- | --- | --- |
| Table object and typed fields | `BookTable.Companion.BOOK`, `BOOK.TITLE`, `BOOK.AUTHOR`, `BOOK.all()` | the query DSL |
| Entity operations | `book.insert()`, `book.update()`, `book.persist()`, `book.delete()` | [entity-operations.md](entity-operations.md) |
| Bulk operations | `Books.insert(books)`, `Books.persist(…)`, `Books.update(…)`, `Books.delete(…)`, `Books.deleteTable()` | the model name plus `s`, also after an `s` (`AccountSettingss`) |
| `SqlType` selectors and conversions for transformed types | `SqlType.BOOK_STATUS`, `BookStatus.PUBLISHED.asValue()` | raw SQL, standalone values |
| View table objects | `AUTHOR_STATS` for `@View class AuthorStats` | [views.md](views.md) |
| The database | `SqliteMagicDatabase` | setup |

- The Gradle plugin, not KSP, generates `SqliteMagicMigrationDatabase` into the unit-test sources of application
  modules for migration tests ([migrations.md](migrations.md)).
- Field names are the column names in upper snake case: `BOOK.TITLE`, `LIBRARY.HOME_STREET`.
- Entity operations are `internal` unless the module sets `publicKotlinExtensionFunctions = true`.
- Generated code is plain Kotlin; read it under `build/generated/ksp/<variant>/kotlin/` to check a field's type. Never
  edit or hand-write generated types, `Table`/`Column` instances or anything needing `@InternalSqliteMagicApi`.
