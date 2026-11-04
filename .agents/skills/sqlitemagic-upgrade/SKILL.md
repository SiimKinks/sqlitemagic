---
name: sqlitemagic-upgrade
description: >-
  Upgrade an Android app or library from SqliteMagic 2.x or 0.x to SqliteMagic 3: dependencies and setup
  (sqliteFactory → driver(...), sqlitemagic-framework-driver or BundledSQLiteDriver, the sqlitemagic-rxjava2,
  sqlitemagic-rxjava3 and sqlitemagic-coroutines adapters, removed Cursor APIs, inTransaction rules), the rewritten
  SQL-first query DSL (legacy Select.from/column/columns, Update.table, Delete.from, asColumn, Expr, lowercase
  predicates such as is/greaterThan/isNull, toColumn/toTable, WITH_ARGS, @ViewQuery with compile() → SELECT FROM …,
  SELECT.COLUMNS, UPDATE TABLE …, typed expressions, mapTo, asScalar), changes that still compile but alter results,
  exceptions or transactions, and the first 3.x release of installed databases (NOT NULL table rebuilds, renamed view
  columns, WAL). Use when upgrading an app, module, sample, test or snippet, porting legacy queries, or fixing compile
  errors and behavior differences after the upgrade, even if the request only says "bump SqliteMagic" or "update the
  queries". Not for later schema migrations of an app already on 3.x, nor for changing or testing SqliteMagic itself.
---

# Upgrading to SqliteMagic 3

Port an app written against SqliteMagic 2.x (or 0.x) to SqliteMagic 3, which rewrites the query DSL, runs the database
on an androidx `SQLiteDriver`, moves reactive support into adapters and makes the schema follow Kotlin nullability.
Most changes are spelling the compiler finds; the costly ones compile and then return different values, throw
different exceptions, run different SQL or change the installed database. Treat an upgrade as done only after every
kind is handled.

## Know the sources

Read the references a task needs, not all of them up front:

- [references/setup.md](references/setup.md): dependencies, choosing a driver, the reactive adapters, transactions
  and connection options. Every upgrade needs it.
- [references/queries.md](references/queries.md): imports, construction order, entity and column selections,
  ordering, joins, subqueries and compounds. Every file that builds a query needs it.
- [references/expressions.md](references/expressions.md): values, raw SQL fragments, predicates, aggregates, text
  helpers and arithmetic.
- [references/statements-and-types.md](references/statements-and-types.md): whole raw SQL, UPDATE/DELETE, views,
  compiled plans and declared types, reading rows without `Cursor`, `Logger`, generated code and removed API.
- [references/behavior.md](references/behavior.md): changes that compile but behave differently, how to find each
  and what to do about it.
- [references/database-upgrade.md](references/database-upgrade.md): releasing the first 3.x database version for
  installed databases, the files on disk, and testing the upgrade.
- In the [SqliteMagic repository](https://github.com/SiimKinks/sqlitemagic), the
  [README](https://github.com/SiimKinks/sqlitemagic/blob/3.0.0/README.md) documents 3.x setup and release
  migrations, the legacy sources are at tag `2.0.0` (`git show 2.0.0:<path>`), and
  `git diff 2.0.0 3.0.0 -- sqlitemagic-sample` is a small worked upgrade of a real app.
- Code on 2.x needs only these references and the 3.0.0 entries of the
  [changelog](https://github.com/SiimKinks/sqlitemagic/blob/3.0.0/CHANGELOG.md). Code on an older 0.x release also
  needs its 2.0.0 entries, such as the move to the KSP processor and the raised minimum API.

## Scope the work

1. Upgrade the plugin and every SqliteMagic artifact together and regenerate: generated tables, columns and readers
   changed with the DSL, so code compiled against old generated sources proves nothing.
2. Inventory legacy call sites before editing, so nothing hides behind the first wave of compile errors. The query
   DSL:

   ```bash
   grep -rnE --include='*.kt' --include='*.java' 'Select\.(from|all|column|columns|distinct|raw|count|avg|sum|min|max|groupConcat|concat|lower|upper|length|abs|format|asColumn|asRawColumn)|\b(Update|Delete)\.(table|from|withConflictAlgorithm)|asColumn|\bExpr\b|\.expr\b|WITH_ARGS|WITH_CONFLICT_ALGORITHM|\bJOIN\b|LEFT_OUTER_JOIN|\.(toColumn|toTable|inTable|toNotNullable|toExpr|setNullable|union|unionAll|intersect|except)\(|\.(asc|desc|isNull|isNotNull)\(\)|`(is|in|as)`|\.(isNot|greaterThan|greaterOrEqual|lessThan|lessOrEqual|notIn|notLike|glob|notGlob|between|notBetween)\(|@ViewQuery|Select\.Companion' <source roots>
   ```

   Setup, reactive, transaction and type API:

   ```bash
   grep -rnE --include='*.kt' --include='*.java' 'sqliteFactory|OpenHelperFactory|SupportSQLite|DbDowngrader|toCursor|getFromCurrentPosition|\bCursor\b|scheduleRxQueriesOn|\.observe\(\)|\.run\(\)|(List|SingleItem|Count)QueryObservable|inTransaction|newTransaction|markSuccessful|OperationFailedException|SQLiteConstraintException|BooleanTransformer|logQueryTime|logWarning|Compiled[A-Za-z]*(Select|Update|Delete)|Query\.Mapper|OrderingTerm|\bNotNullable\b|(Numeric|Boolean|Complex|Function|Raw|Selection)Column\b|\bMigrationDatabase\b' <source roots>
   ```

   Also list the build files that declare SqliteMagic, `androidx.sqlite` or RxJava, hand-written migration scripts
   (`src/*/assets/*.sql`), and code that copies or backs up the database file. Expect false positives; the lists scope
   the work, the compiler confirms it. Note every query that joins, aliases, divides, aggregates, counts, nests a
   query, compounds, reads a view through raw SQL or catches entity-operation failures: those are the behavior
   checklist's sites.
3. The rewritten DSL targets Kotlin. Port Java code that builds queries, and Java `@View` models, to Kotlin rather
   than writing Java against the new stages; Java table models stay supported if they are public.

## Upgrade in order

**Step 1, setup.** Follow [setup.md](references/setup.md): dependencies, `driver(…)`, adapter imports, transaction
blocks. Keep the device's SQLite (`FrameworkSQLiteDriver`) and the project's reactive library unless the user decides
otherwise.

**Step 2, spelling.** Rewrite each site with the spelling references, file by file, compiling often. The rules
behind most edits:

- Queries are immutable values. Every call returns a new query, so reassign or chain; a discarded result does nothing.
  Legacy `usingConnection()` and `queryDeep()` mutated a shared builder, so ignored results used to work.
- Clauses follow SQL order: selection before `FROM`, `DISTINCT` right after `SELECT`, a join's `ON`/`USING` after the
  joined source. `queryDeep()`, `readAs`, `mapTo` and `usingConnection()` are final configuration after the last
  clause; terminals (`execute()`, `useRows { }`, `takeFirst()`, `count()`, `compile()`, `observe()`) come last.
- One uppercase spelling per SQL construct, callable infix or with a dot. Lowercase Java-style builders
  (`.where(…)`, `.orderBy(…)`, `.leftJoin(…)`) become the uppercase dot-call (`.WHERE(…)`, `.ORDER_BY(…)`).
- Keep each call site's existing style: infix stays infix, fluent stays fluent. Kotlin infix precedence is not SQL
  precedence, so keep or add the parentheses around infix operands.
- Prefer inference over spelling the new generic types; delete explicit legacy type annotations where possible.
- `observe()` and `run()` come from the reactive adapter's package (`com.siimkinks.sqlitemagic.rxjava2`, `.rxjava3`
  or `.coroutines`); `Cursor` reads become `useRows`.

Do not recreate legacy names with local extension functions, wrappers or typealiases to shrink the diff. They keep
legacy semantics alive in the caller's head while the engine behaves differently, and they hide the sites the
behavior pass must review.

**Step 3, behavior.** Walk [behavior.md](references/behavior.md) for every migrated query and for the code around
entity operations, transactions and views, not only the sites that needed edits: several changes apply to code whose
spelling is unchanged. Fix the cause the checklist names (lift a field with `.nullable()`, select a required property,
join explicitly, choose `SUM` or `TOTAL`, catch `SQLException`) instead of silencing it with `!!`, broad catches or
`readAs` casts. When an item needs a product decision, such as whether a result should keep legacy semantics, settle
it with the concrete before/after rather than picking one silently.

**Step 4, installed databases.** Follow [database-upgrade.md](references/database-upgrade.md): preview the generated
migration, resolve the rows it would delete, raise `@Database(version)`, run `migrate<Variant>Db` and commit the
release. Every app with a shipped file database needs this step; raising the version alone leaves installed tables
without `NOT NULL`.

## Verify

- Compile every migrated source set, including tests, after regeneration.
- Run the tests covering migrated queries. Rejections before SQL, changed values and changed exceptions only show at
  runtime, preferably against a real SQLite database (Android device or emulator, or a JVM SQLite driver). Add a
  focused test where a behavior item applies and nothing covers it.
- Run the JVM migration tests after the release is published, and upgrade a device or emulator from the last 2.x
  build with data in place ([database-upgrade](references/database-upgrade.md#test-the-upgrade)).
- Record the migrated files, the behavior items that applied and how each was resolved, the migration warnings and
  how each was resolved, the decisions still open, and which runtime behavior no test exercised, for example in the
  change's description. Do not claim behavior parity that only compilation established.
