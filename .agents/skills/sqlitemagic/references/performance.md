# Performance

SqliteMagic adds little on top of SQLite: generated readers and binders, no reflection, direct cursor-window reads on
the framework driver, a per-connection statement cache. What costs time is the SQL an app asks for and how often it
asks. Apply these in order of impact.

## Contents

- [Ask SQLite for less](#ask-sqlite-for-less)
- [Write in batches](#write-in-batches)
- [Index for the queries you run](#index-for-the-queries-you-run)
- [Reuse compiled work](#reuse-compiled-work)
- [Observe narrowly](#observe-narrowly)
- [Threads and connections](#threads-and-connections)
- [Measure](#measure)

## Ask SQLite for less

- **Select only what the code reads.** `SELECT FROM BOOK` reads every column of every book and of its loaded
  relationships, joined. A list row that shows a title and an author name selects those two fields and maps them
  (`SELECT.COLUMNS(…).mapTo(::Row)`).
- **Do not load related objects you will not use.** Leave off `queryDeep()` unless the code reads related objects'
  values: without it, a related model that can be built from its ID alone is read as ID-only. One with other required
  properties is always joined; select columns instead when the code does not need it. A `handleRecursively = false`
  relationship is always read as ID-only, `queryDeep()` included; the flag also changes writes (it keeps
  `onDeleteCascade`, so the parent row must be written first), so choose it for who owns the related rows, not to save
  a read.
- **Count, test and aggregate in SQL.** `count()` instead of `execute().size`; `takeFirst()` or `EXISTS` instead of
  reading a list to check emptiness; `SUM`/`MAX`/`GROUP_BY` instead of folding in Kotlin.
- **Filter and paginate in SQL.** `WHERE` and `LIMIT` instead of `filter`/`take` on a full list; keyset pagination
  instead of large `OFFSET`s ([queries.md](queries.md#filtering-grouping-ordering-pagination)).
- **One query instead of N.** Join or use `IN query` instead of querying per item in a loop; a subquery column
  (`asScalar() AS "…"`) or a grouped derived source instead of a count query per row.
- **Stream large results.** `useRows { rows -> … }` reads rows lazily without building the whole list, and can stop
  early. Keep the block short; it holds a connection.
- **Keep big blobs out of hot rows.** Rows larger than a cursor window (~2 MB) cannot be read on the framework driver;
  large blobs in a frequently read table slow every read of it. Store them in their own table or as files.

## Write in batches

- Many objects: `Books.insert(list)`, `Books.persist(list)`, `Books.update(list)`, `Books.delete(list)` run in one
  transaction with one notification (recursive writes under `CONFLICT_IGNORE` commit each object graph on its own).
- Several different writes: one `inTransaction { }` or `withTransaction { }`; outside a transaction every statement
  is its own commit.
- Rows chosen by a condition: one UPDATE or DELETE statement instead of loading entities and writing them back.
- Avoid `CONFLICT_REPLACE` for upserts: it deletes and re-inserts, cascading deletes and rewriting indexes; `persist()`
  updates in place.

## Index for the queries you run

- Index every `onDeleteCascade` column as the first column of an index (`@Index` on `val author: Author`): each
  parent delete looks up its children by it, a full scan of the child table without one. Index other foreign keys
  that queries filter or join by, and columns that `WHERE` plus `ORDER_BY … LIMIT` use together, as one composite
  index; its columns follow property declaration order, so declare them in that order.
- Unique lookups (`@Unique`, unique indexes) are indexed already, as is the `@Id`.
- Expressions over a column (`LOWER(x)`, `x + 1`, `CAST`) and leading-wildcard `LIKE "%…"` cannot use a plain index;
  store a normalized column when such a lookup is frequent.
- Check a plan with raw SQL in a test or the Database Inspector:
  `SELECT.RAW("EXPLAIN QUERY PLAN SELECT …").useRows { … }`, or read the SQL of a typed query from `compile().sql`.
- Every index slows inserts and updates; add the ones a real query needs.

## Reuse compiled work

- `execute()` compiles (resolves, renders and binds readers for) the query every time. For a query run many times, for
  example per list item, per keystroke or in a loop, keep `compile()`'s plan in a property and bind parameters per call
  ([queries.md](queries.md#compiled-plans-and-parameters)). `takeFirst()` and `count()` return plans too.
- Each connection caches up to 25 prepared SQLite statements by SQL text. Stable SQL text hits that cache: bound values
  (`asValue()`, host values in comparisons) keep the text stable; `asLiteral()` and `IN` lists of varying length
  change it. Prefer bound values, and an `IN query` over huge or highly variable `IN` lists.
- Queries are immutable: define shared ones once (companion `val`, repository property) rather than rebuilding
  identical queries on every call. Building is cheap, but free is cheaper in hot paths.

## Observe narrowly

- An observed query re-runs after every committed change to any table it reads. Observing `SELECT FROM BOOK` on a
  screen that shows ten titles re-reads every book on every write: select the shown columns with a `LIMIT`.
- Splitting a screen into several small observed queries (list, count badge, header) lets each re-run alone and keeps
  each cheap.
- Raw queries observe only the tables they declare; a raw fragment adds its declared tables to the query's own.
  Declare exactly what they read.
- Do not create observers per list item; observe the list.

## Threads and connections

- Never run blocking terminals on the main thread; use the adapters ([reactive.md](reactive.md)).
- Reads run in parallel on up to four reader connections; one writer serializes writes. Long transactions block other
  writers, so batch work into short transactions or call `yieldIfContended()`/`yieldIfContendedSafely()` in long
  imports (`yieldIfContendedSafely()` throws in a nested transaction, see [transactions.md](transactions.md)).
- Lower `readerConnections` only to save memory in apps that rarely read concurrently. `0` leaves journal mode,
  `synchronous` and `wal_autocheckpoint` to the driver and `configureConnection`: a new file gets the driver's rollback
  journal and `FULL`, a sync per commit; a file already in WAL stays in WAL on both drivers unless `configureConnection`
  sets `PRAGMA journal_mode`.
- With reader connections the library sets `PRAGMA synchronous = NORMAL`, the fast setting that keeps a WAL database
  intact: a commit writes to the WAL without a sync. `FULL` costs a sync per commit.
- With reader connections the library also sets SQLite's default `PRAGMA wal_autocheckpoint = 1000` (the framework
  driver would checkpoint every 100 pages, about 400 KB). Bulk writes checkpoint less often, and each checkpoint
  takes longer.

## Measure

Measure on a release build on a device. Time the code path (the library does not log query times), check query plans
before and after adding an index, and prefer fewer, narrower queries over micro-optimizing one.
