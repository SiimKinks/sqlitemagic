# Entity operations

Saving and deleting whole objects through the generated `insert()`, `update()`, `persist()` and `delete()`
extensions and the bulk object (`Books`). Changing columns of rows chosen by a condition is an UPDATE or DELETE
statement instead ([statements.md](statements.md)).

## Contents

- [Single objects](#single-objects)
- [Bulk operations](#bulk-operations)
- [Options](#options)
- [Choosing the operation](#choosing-the-operation)
- [Related objects](#related-objects)
- [Tables without an ID](#tables-without-an-id)
- [Failures](#failures)
- [Async forms](#async-forms)

## Single objects

Each extension returns a builder; nothing runs until `execute()` (blocking) or an adapter's `await()`/`observe()`.

```kotlin
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.update

val result: EntityInsertResult = Book(
  title = "Dune",
  pages = 412,
  author = Author(name = "Frank Herbert")
)
  .insert()
  .execute()
val bookId = (result as? EntityInsertResult.Inserted)?.rowId

val updated: Boolean = book.copy(title = "Dune Messiah")
  .update()
  .execute()

val persisted: EntityPersistResult = book
  .persist()
  .execute()

val deletedRows: Int = book
  .delete()
  .execute()
```

| Operation | SQL | Result |
| --- | --- | --- |
| `insert()` | `INSERT` | `EntityInsertResult.Inserted(rowId)` or `Ignored` (a conflict the algorithm ignored) |
| `update()` | `UPDATE … WHERE id = ?` | `true`, or `false` when no row has that ID |
| `persist()` | `UPDATE` by ID, `INSERT` when nothing was updated or the ID is null | `EntityPersistResult.Inserted(rowId)`, `Updated` or `Ignored` |
| `delete()` | `DELETE … WHERE id = ?` | deleted row count |

`rowId` is SQLite's row ID (`null` for `WITHOUT ROWID` tables); for an auto-increment `Long` ID it is the new ID. An
immutable model is not updated with it, so use it, or the returned row, from here on.

## Bulk operations

The generated object named by the model name plus `s` (`Books`, `Authors`; `AccountSettingss`) writes collections in
one transaction, reusing one prepared statement:

```kotlin
val allInserted: Boolean = Books
  .insert(books)
  .execute()

Books
  .persist(fromServer)
  .execute()

val deleted: Int = Books
  .delete(selected)
  .execute()

val cleared: Int = Books
  .deleteTable()
  .execute()
```

- Insert, update and persist return `true` when at least one object's write committed. Without `CONFLICT_IGNORE` a
  bulk write is all or nothing: a conflict that SQLite reports throws, and an object whose write applies nothing (an
  update whose row is missing) makes the operation return `false`; both roll every object back. Use `persist` to
  insert missing rows instead, or `CONFLICT_IGNORE` to skip them and conflicting objects (for objects with recursively
  written relationships, each object graph then commits or rolls back on its own).
- Inside a caller's transaction, a bulk write runs in a savepoint that undoes only its own changes when it fails.
- Use bulk operations instead of a loop of single operations: a loop outside a transaction commits, syncs and notifies
  observers once per object.
- `deleteTable()` deletes every row of the table; `DELETE FROM BOOK` does the same as a statement.

## Options

All builders take `usingConnection(connection)`; writes take a conflict algorithm; `persist` takes `ignoreNullValues()`;
`update` and `persist` (single and bulk) take `byColumn(uniqueColumn)`:

```kotlin
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE

val firstSeen = tag
  .insert()
  .conflictAlgorithm(CONFLICT_IGNORE)
  .execute() is EntityInsertResult.Inserted

author
  .persist()
  .ignoreNullValues()
  .execute()

user
  .update()
  .byColumn(USER.EMAIL)
  .execute()
```

- `conflictAlgorithm(…)` takes `SQLiteDatabase.CONFLICT_ABORT` (the default behavior), `CONFLICT_IGNORE`,
  `CONFLICT_REPLACE`, `CONFLICT_FAIL`, `CONFLICT_ROLLBACK` or `CONFLICT_NONE`, rendered as `INSERT OR …`/
  `UPDATE OR …`.
- `ignoreNullValues()` leaves out properties that are `null` in the object: an update keeps the stored values of those
  columns, an insert lets them take their SQL defaults. It is the way to write back a partial `mapTo(TABLE)` object,
  whose unselected properties read `null`. It leaves out nothing else: an ID-only related object from a shallow read
  holds its Kotlin defaults for the other properties, and those are written ([related objects](#related-objects)).
- `byColumn(column)` identifies the row by a non-null `@Unique` column instead of the ID; the column must belong to the
  model's own table.
- Builder calls configure the builder and return it: chain them, and configure each operation in one chain.

## Choosing the operation

| Need | Use |
| --- | --- |
| Save a new object, the ID from SQLite | `insert()`, take `Inserted.rowId` |
| Save an object that may or may not exist (sync from a server, "save" button) | `persist()`; bulk `Items.persist(list)`; an INSERT never writes an auto-increment ID, so server IDs need `@Id(autoIncrement = false)` ([models.md](models.md#ids)) |
| Save only if absent, keep the existing row (a `@Unique` column or app-assigned ID decides) | `insert().conflictAlgorithm(CONFLICT_IGNORE)` |
| Replace an existing object completely, by ID | `update()` |
| Change some columns, increment a counter, change many rows | `UPDATE TABLE … SET … WHERE …` ([statements.md](statements.md)) |
| Remove one known object | `delete()` |
| Remove rows chosen by a condition | `DELETE FROM … WHERE …` |
| Remove everything | `Items.deleteTable()` |

- Prefer `persist()` over `CONFLICT_REPLACE` for upserts: `REPLACE` deletes the conflicting row and inserts a new one,
  which fires `ON DELETE CASCADE` on its children and gives an auto-increment row a new ID.
- Prefer an UPDATE statement over read-modify-write of an entity when only some columns change: one statement, no race
  between the read and the write, no risk of overwriting columns another writer changed.

## Related objects

With recursively handled relationships (the default), writing a `Book` also writes its `Author`, and its editor:

- `insert()` and `persist()` write every recursively handled related object first, then the parent stores their
  (possibly new) IDs as foreign keys; `update()` updates the parent, then its related objects. The whole graph runs
  in one transaction, or a savepoint inside the caller's transaction, and a failure rolls all of it back.
- So `insert()` of a book whose author already exists inserts the author again. An auto-increment ID is never
  inserted, so that silently creates a second copy of the author with a new ID and points the book at it; with an
  app-assigned ID or a `@Unique` column it fails with a constraint violation. Add a child of an existing parent with
  `persist()` (which updates the parent and inserts or updates the child), or declare the relationship
  `handleRecursively = false` when the child never writes its parent. An
  `update()` whose related row is missing rolls back the parent's update too and returns `false`.
- Writing a parent overwrites the related object's columns with the values in memory. A related object from a shallow
  read holds its ID and, for its other properties, `null` or their Kotlin defaults. `persist().ignoreNullValues()`
  keeps the stored columns only for the `null` ones: when the model has non-null defaults, read the parent with
  `queryDeep()`, change the parent's own columns with an UPDATE statement, or make the relationship
  `handleRecursively = false` when the parent should never write it.
- Delete removes only the object's own row; children go with `onDeleteCascade`, otherwise delete them first.

## Tables without an ID

A table without `@Id` supports `insert()` and bulk insert. When it has a non-null single-column `@Unique` property,
`update()`, `persist()` and `delete()` (single and bulk) take that column in their terminal:

```kotlin
settings
  .persist()
  .execute(ACCOUNT_SETTINGS.ACCOUNT_KEY)
```

Without such a column, change and delete rows with UPDATE and DELETE statements.

## Failures

- SQLite errors are thrown as they are: `android.database.SQLException`. `FrameworkSQLiteDriver` throws Android's
  typed subclasses (`SQLiteConstraintException`, …), `BundledSQLiteDriver` plain `SQLException` with the result code
  in the message. Catch `SQLException` so the code works on both; catch around the smallest operation whose failure
  the code can handle.
- `OperationFailedException` (in `com.siimkinks.sqlitemagic.exception`) reports outcomes SQLite completes without an
  error: an insert or persist that wrote no row under a non-ignoring conflict algorithm, a skipped relationship write,
  a relationship whose ID resolved to NULL, an RxJava `update().observe()` that updated no row (unless
  `CONFLICT_IGNORE`), and an RxJava bulk `observe()` in which one of these entity outcomes, or an update that found no
  row, rolled back the whole operation. SQLite errors are not `OperationFailedException`: they arrive as the
  `SQLException` itself.
- A failed single write leaves the caller's transaction usable, except a `CONFLICT_ROLLBACK` conflict, which ends the
  transaction (every later statement throws `IllegalStateException`); a failed bulk or graph write undoes its own
  savepoint. The exception still propagates, so an `inTransaction { }` that does not catch it rolls back.
- Choosing a conflict algorithm is better than catching a constraint exception when the conflict is an expected
  outcome (a duplicate tag, a re-delivered event).

## Async forms

The adapters add the async terminals (import from the adapter's package):

| Builder | Coroutines `await()` | RxJava `observe()` |
| --- | --- | --- |
| `insert()` | `EntityInsertResult` | `Single<EntityInsertResult>` |
| `update()` | `Boolean` | `Completable`, fails with `OperationFailedException` when no row was updated, unless `CONFLICT_IGNORE` |
| `persist()` | `EntityPersistResult` | `Single<EntityPersistResult>` |
| `delete()`, bulk `delete(…)`, `deleteTable()` | `Int` | `Single<Int>` |
| bulk `insert`/`update`/`persist` | `Boolean` | `Completable`, fails with `OperationFailedException` when an entity write completed without writing, which rolls back the operation |
| by-column terminals | `await(column)` | `observe(column)` |

Coroutine `await()` runs on the adapter's dispatcher and joins an enclosing `withTransaction`. RxJava write
`Single`s and `Completable`s are cold and run on the subscribing thread: add `subscribeOn(Schedulers.io())`. See
[reactive.md](reactive.md).
