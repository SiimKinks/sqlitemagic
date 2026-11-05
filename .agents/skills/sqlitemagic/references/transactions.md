# Transactions, threads and connections

## Contents

- [When to use a transaction](#when-to-use-a-transaction)
- [Blocking transactions](#blocking-transactions)
- [Suspending transactions](#suspending-transactions)
- [Rules inside a transaction](#rules-inside-a-transaction)
- [Threads and connections](#threads-and-connections)
- [Errors](#errors)

## When to use a transaction

- Several writes that must all happen or none: create a parent and its children, move a row between states, apply a
  server sync.
- Many writes in a row, for speed: one commit instead of one per statement. (Bulk entity operations already use one.)
- A read followed by a write that depends on it, so no other writer interleaves.

A single entity operation, bulk operation or statement is already atomic and needs no transaction of its own; an
entity write with related objects runs its own transaction. The exception is a bulk write of objects with recursive
relationships under `CONFLICT_IGNORE`, which commits each object graph on its own.

## Blocking transactions

```kotlin
import com.siimkinks.sqlitemagic.inTransaction

fun moveBooks(fromAuthorId: Long, toAuthorId: Long) = inTransaction {
  checkNotNull((SELECT COLUMN AUTHOR.ID FROM AUTHOR WHERE (AUTHOR.ID IS toAuthorId)).takeFirst().execute()) {
    "Author $toAuthorId no longer exists"
  }
  (UPDATE TABLE BOOK SET (BOOK.AUTHOR to toAuthorId) WHERE (BOOK.AUTHOR IS fromAuthorId))
    .execute()
}
```

- `inTransaction { }` (default connection) or `connection.inTransaction { }` commits when the block returns, including
  through `return@inTransaction`, and rolls back and rethrows when it throws. It cannot suspend or `return` from the
  enclosing function. Throw to roll back.
- `newTransaction()` gives manual control: `markSuccessful()` before `end()`, or `use { }`:

  ```kotlin
  SqliteMagic.newTransaction().use { transaction ->
    books.forEach { book ->
      book.persist().execute()
    }
    transaction.markSuccessful()
  }
  ```

- Nested `inTransaction`/`newTransaction` calls join the outer transaction; any unsuccessful level rolls back the whole
  transaction when the outermost one ends.
- A blocking transaction belongs to its thread: run it on a background thread (in RxJava, inside
  `Completable.fromAction { inTransaction { … } }.subscribeOn(Schedulers.io())`, as the sample app does).
- A long-running batch can let other writers in with `transaction.yieldIfContendedSafely()`, which commits the work so
  far. It throws in a nested transaction, where the coroutine `yieldIfContended()` does nothing and returns `false`, so
  only the coroutine form is safe in code that callers may run inside their own transaction.

## Suspending transactions

```kotlin
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQueryOnce
import com.siimkinks.sqlitemagic.coroutines.withTransaction

suspend fun renameAuthor(id: Long, name: String) = withTransaction {
  val author = (SELECT FROM AUTHOR WHERE (AUTHOR.ID IS id))
    .takeFirst()
    .observe()
    .runQueryOnce()
  author
    ?.copy(name = name)
    ?.update()
    ?.await()
}
```

- `withTransaction { }` (default connection) or `connection.withTransaction { }` commits when the block and the
  coroutines it launched complete; it rolls back when they fail or are cancelled. Every call of the connection made by
  the block and the coroutines it launches takes part in it, and one-shot reads see its changes. Writes from other
  threads or coroutines wait for it to end; their reads see the last committed data (with `readerConnections(0)`,
  they wait too).
- Inside the block, use `await()`, `runQueryOnce()`, and blocking `execute()` for code that cannot suspend. Never call
  `runBlocking` inside it: that coroutine runs outside the transaction, waits for it, and can deadlock.
- `yieldIfContended()` (a member of the block's scope) commits the work so far and lets waiting writers run; use it in
  long loops.
- A cancellation that arrives during the commit still throws `CancellationException` although the transaction
  committed; make the work idempotent where that matters.

## Rules inside a transaction

- Keep transactions short and free of network calls, file I/O and UI waits: a transaction holds the only writer
  connection, and every other writer waits (up to `connectionPoolTimeout`, then fails).
- Reads on other threads continue and see the last committed data, not the transaction's changes.
- Observers are notified after the commit. Do not collect or subscribe to observed queries inside a transaction; read
  with `execute()`, and inside `withTransaction` also with `runQueryOnce()`.
- Catching an exception inside the block does not undo the failed operation's effects on the transaction's success:
  a single failed write leaves the transaction usable, a failed bulk or graph write undoes its own savepoint. Decide
  explicitly whether to continue or rethrow. After a statement that made SQLite roll back the whole transaction (a
  `CONFLICT_ROLLBACK` conflict), every further statement throws `IllegalStateException`; end the transaction.
- Do not write between `markSuccessful()` and `end()`.

## Threads and connections

- Blocking terminals (`execute()`, `useRows`, entity `execute()`) run on the calling thread; never call them on the
  main thread. StrictMode or ANRs are the symptom.
- One writer and up to four readers (WAL): reads run in parallel with each other and with the writer.
- `useRows` holds a connection until its block returns; keep the block short and do not start other blocking database
  work that waits for the same connection.
- `usingConnection(connection)` on a query, statement or entity operation targets another `DbConnection`, for example
  one opened with `openNewConnection()` for a separate database file. Nested queries use the outermost query's
  connection.
- Temporary tables (`TableOption.TEMPORARY`) are per connection: queries on them run on the writer connection.

## Errors

| Exception | Cause |
| --- | --- |
| `android.database.SQLException` (and framework subclasses such as `SQLiteConstraintException`) | SQLite rejected a statement: constraint, syntax, missing table, a feature the engine lacks; also a connection wait over `connectionPoolTimeout` |
| `IllegalArgumentException` | a query rejected before SQL: outer-joined non-null field, ambiguous or missing relationship path, partial entity missing a required property, compound arm mapped with a function |
| `IllegalStateException` | transaction misuse: reading a reactive query on a thread holding a blocking transaction, statements after a transaction-wide rollback |
| `OperationFailedException` | an entity write SQLite completed without writing ([entity-operations.md](entity-operations.md#failures)) |

The pre-SQL rejections are programming errors: fix the query rather than catch them. They happen when the query is
prepared, not when Kotlin compiles it, and `compile()` (or `takeFirst()`/`count()`) prepares without a database, so a
plain JVM unit test that calls it on each query, or reads its `sql`, catches them. Parameters are checked when they
are bound: `bind(…)` with a missing, repeated or foreign parameter, or executing a parameterized plan unbound, throws
`IllegalArgumentException`, so a test catches those only by binding the plan.
