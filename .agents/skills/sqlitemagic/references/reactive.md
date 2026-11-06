# Coroutines and RxJava

Observed queries that re-run when their tables change, and one-shot async work, through the
`sqlitemagic-coroutines`, `sqlitemagic-rxjava3` and `sqlitemagic-rxjava2` adapters. Use the adapter the project
already uses; the RxJava 2 and 3 adapters have the same API in `com.siimkinks.sqlitemagic.rxjava2`/`.rxjava3`.

## Contents

- [How observation works](#how-observation-works)
- [Coroutines](#coroutines)
- [RxJava](#rxjava)
- [Patterns](#patterns)

## How observation works

- `observe()` on a query or plan returns a cold stream of *notifications*; a `runQuery…` operator runs the query for
  each and emits results. It emits once on collection or subscription, then after every committed change to a table
  the query reads: its source tables, joined and automatically joined tables, subqueries, CTEs, view definitions, and
  tables declared with `observes(…)`.
- Changes are combined: a burst of writes costs one or two further queries, not one per write. An emission can lag
  writes made while it was delivered, but the last emission reflects the final data. Never count emissions as writes.
- Notifications come from writes through the library: entity operations, statements, raw statements' `notifies`,
  `clearData()`. Writes by other processes or undeclared raw SQL are not seen.
- A delete through an entity operation or a `DELETE` statement also notifies the tables that `ON DELETE CASCADE`
  reaches from the deleted table, and so does such a write with `CONFLICT_REPLACE`, which deletes the row it replaces:
  a query over a child table re-runs when deleting its parent removes its rows.
- Writes inside a transaction notify after it commits, and not at all if it rolls back, unless it yielded
  (`yieldIfContended…`), which commits the earlier work.
- An observed query on a closed connection completes without emitting. One-shot forms with a nullable or `Maybe`
  result then return `null` or complete empty; the `List` and `…OrDefault` forms fail with `NoSuchElementException`.

## Coroutines

```kotlin
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.isNotZero
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQuery
import com.siimkinks.sqlitemagic.coroutines.runQueryOnce

class BookRepository {
  fun books(authorId: Long): Flow<List<Book>> = Book.byAuthor(authorId)
    .observe()
    .runQuery()

  fun hasDrafts(): Flow<Boolean> = (SELECT FROM BOOK WHERE (BOOK.STATUS IS BookStatus.DRAFT))
    .count()
    .observe()
    .isNotZero()

  suspend fun book(id: Long): Book? = (SELECT FROM BOOK WHERE (BOOK.ID IS id))
    .takeFirst()
    .observe()
    .runQueryOnce()

  suspend fun save(book: Book): EntityPersistResult = book
    .persist()
    .await()

  suspend fun publish(id: Long): Int = (
      UPDATE
          TABLE BOOK
          SET (BOOK.STATUS to BookStatus.PUBLISHED)
          WHERE (BOOK.ID IS id)
      )
    .await()
}
```

| Source | `observe()` | Stream operators | One-shot |
| --- | --- | --- | --- |
| query, `compile()` plan, raw query with `observes` | `Flow<Query<List<T>>>` | `runQuery(): Flow<List<T>>` | `runQueryOnce(): List<T>` |
| `takeFirst()` plan | `Flow<Query<T?>>` | `runQuery(): Flow<T>` (skips `null`), `runQueryOrDefault(d)` | `runQueryOnce(): T?`, `runQueryOnceOrDefault(d)` |
| `count()` plan | `Flow<Query<Long>>` | `runQuery()`, `isZero()`, `isNotZero()` | `runQueryOnce(): Long?` |
| entity operations, UPDATE/DELETE, raw statements, raw SELECT without `observes` | — | — | `await()` |

- Import `observe`, the operators and `await` from `com.siimkinks.sqlitemagic.coroutines`.
- Queries run on the dispatcher set with `dispatchCoroutineQueriesOn` (default `Dispatchers.IO`); collectors receive
  results in their own context. Do not add `flowOn(Dispatchers.IO)` or `withContext(Dispatchers.IO)` around them.
- Do not run blocking writes (`execute()`, blocking transactions) on that dispatcher's threads outside `withTransaction`:
  a coroutine write waiting for the writer resumes there, so a single-thread or exhausted dispatcher deadlocks.
- Apply `runQuery` directly to `observe()`; transform the result flow afterwards (`map`, `distinctUntilChanged`,
  `stateIn`).
- `withTransaction { }` runs suspending work in one transaction; inside it `await()`, `runQueryOnce()` and blocking
  `execute()` take part in it ([transactions.md](transactions.md#suspending-transactions)). Collecting an observed
  query inside it fails.
- A thread holding a blocking transaction (`inTransaction`, `newTransaction`) cannot read reactive queries; use
  `execute()` there.

## RxJava

```kotlin
import com.siimkinks.sqlitemagic.rxjava3.observe

fun books(authorId: Long): Observable<List<Book>> = Book.byAuthor(authorId)
  .observe()
  .runQuery()

fun book(id: Long): Maybe<Book> = (SELECT FROM BOOK WHERE (BOOK.ID IS id))
  .takeFirst()
  .observe()
  .runQueryOnce()

fun save(book: Book): Completable = book
  .persist()
  .observe()
  .ignoreElement()
  .subscribeOn(Schedulers.io())
```

| Source | `observe()` | Operators |
| --- | --- | --- |
| query, `compile()` plan, raw query with `observes` | `ListQueryObservable<T>` | `runQuery(): Observable<List<T>>`, `runQueryOnce(): Single<List<T>>` |
| `takeFirst()` plan | `SingleItemQueryObservable<T>` | `runQuery()` (skips `null`), `runQueryOrDefault(d)`, `runQueryOnce(): Maybe<T>`, `runQueryOnceOrDefault(d): Single<T>` |
| `count()` plan | `CountQueryObservable` | `runQuery()`, `runQueryOnce(): Maybe<Long>`, `isZero()`, `isNotZero()` |
| raw SELECT without `observes` | `Single` (list) or `Maybe` (first row), run once | — |
| entity operations | `Single<…>` or `Completable` ([table](entity-operations.md#async-forms)) | — |
| UPDATE/DELETE, raw statements, `CompiledMutation` | `Single<Int>` | — |

- Import `observe` (and `run`, `scheduleRxQueriesOn`) from `com.siimkinks.sqlitemagic.rxjava3` or `.rxjava2`; the
  operators are members. A file using both adapters imports one with an alias.
- Query streams run and emit on `Schedulers.io()` unless `scheduleRxQueriesOn(scheduler)` sets another; add
  `observeOn(AndroidSchedulers.mainThread())` for UI.
- `Query.run()` and the write `Single`s and `Completable`s are cold and run on the subscribing thread: add
  `subscribeOn(Schedulers.io())`. Each subscription runs the query or write again.
- Dispose subscriptions with the lifecycle that started them.

## Patterns

- **Observe what the screen shows.** A list screen observes its list query; a detail screen observes the item; a badge
  observes a `count()`. The streams re-run independently and only when their own tables change.
- **An item that may disappear.** `takeFirst().observe().runQuery()` skips `null`, so it never reports deletion. When
  absence matters, observe a one-row list and map it:
  `query.LIMIT(1).observe().runQuery().map(List<Book>::firstOrNull)` (RxJava: wrap in `Optional` or use a sealed
  state).
- **Build streams once.** Create the flow or observable in a repository or ViewModel, not on every recomposition or
  `onResume`; in Compose, `remember` it or collect a `StateFlow`:

  ```kotlin
  val books: StateFlow<List<Book>> = repository.books(authorId)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList()
    )
  ```

- **Parameters that change.** Switch streams with `flatMapLatest` (coroutines) or `switchMap` (RxJava) over the
  parameter, building the query for each value, or observe a bound compiled plan
  ([queries.md](queries.md#compiled-plans-and-parameters)).
- **Select narrowly for observed queries.** They re-run on every change to their tables; a narrow selection with a
  `LIMIT` keeps each run cheap.
- **Debounce in the UI layer, not in the database layer.** Observation already conflates bursts.
