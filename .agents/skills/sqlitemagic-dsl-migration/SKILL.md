---
name: sqlitemagic-dsl-migration
description: >-
  Migrate Kotlin code from SqliteMagic's legacy query DSL (Select.from/column/columns, Update.table,
  Delete.from, asColumn, Expr, lowercase predicates such as is/greaterThan/isNull, toColumn/toTable,
  WITH_ARGS, @ViewQuery with compile()) to the rewritten SQL-first DSL (SELECT FROM …, SELECT.COLUMNS,
  UPDATE TABLE …, typed expressions, mapTo, asScalar), including changes that still compile but alter
  results, exceptions or SQL meaning. Use when upgrading an app, module, sample, test or snippet to the
  new DSL, porting legacy queries, or fixing compile errors and behavior differences after the upgrade,
  even if the request only says "update the queries". Also use when upgrading to SqliteMagic 3 setup:
  sqliteFactory → driver(...), the sqlitemagic-framework-driver, sqlitemagic-rxjava2/rxjava3 and
  sqlitemagic-coroutines artifacts, and removed Cursor APIs. Not for changing the DSL itself
  (sqlitemagic-dsl-rewrite) or testing its implementation (sqlitemagic-dsl-testing).
---

# SqliteMagic DSL migration

Port code written against the legacy query DSL (the pre-rewrite library, released as 0.x and 2.0.0) to the rewritten
DSL of SqliteMagic 3.
Most changes are spelling the compiler finds; the costly ones compile and then return different values, throw
different exceptions or run different SQL. Treat a migration as done only after both kinds are handled.

## Know the sources

- Legacy → new spelling, with before/after code. Read the files a call site needs, not all of them up front:
  - [references/queries.md](references/queries.md): imports, construction order, entity and column selections,
    ordering, joins, subqueries and compounds. Every file that builds a query needs it.
  - [references/expressions.md](references/expressions.md): values, raw SQL fragments, predicates, aggregates, text
    helpers and arithmetic.
  - [references/statements-and-types.md](references/statements-and-types.md): whole raw SQL, UPDATE/DELETE, views,
    compiled plans and declared types, `Logger`, generated code and removed API.
- [references/behavior.md](references/behavior.md): changes that compile but behave differently, how to find each
  and what to do about it.
- In the SqliteMagic repository, the legacy sources are at tag `2.0.0` (`git show 2.0.0:<path>`), and
  `git diff 2.0.0 16849610 -- sqlitemagic-sample` is a small worked migration of a real app.
- Maintainers' local design notes, outside version control, keep the authoritative record of every agreed break
  (`docs/dsl/BREAKING_CHANGES.md`) and of the final behavior (`docs/dsl/DESIGN.md`). That log also records
  intermediate states of the unreleased rewrite; a legacy consumer never saw those, so the references keep only
  legacy → final differences. When the log and a reference disagree, the log wins; fix the reference.
- Code on 2.0.0 needs only these references and the 3.0.0 entries of the project's changelog. Code on an older 0.x
  release also needs the 2.0.0 changes, such as the move to the KSP processor; check the changelog for those.

## Scope the work

1. Upgrade the runtime and processor together and regenerate: generated tables, columns and readers changed with the
   DSL, so code compiled against old generated sources proves nothing.
2. Inventory legacy call sites before editing, so nothing hides behind the first wave of compile errors:

   ```bash
   grep -rnE --include='*.kt' --include='*.java' 'Select\.(from|all|column|columns|distinct|raw|count|avg|sum|min|max|groupConcat|concat|lower|upper|length|abs|format|asColumn|asRawColumn)|\b(Update|Delete)\.(table|from|withConflictAlgorithm)|asColumn|\bExpr\b|\.expr\b|WITH_ARGS|WITH_CONFLICT_ALGORITHM|\bJOIN\b|LEFT_OUTER_JOIN|\.(toColumn|toTable|inTable|toNotNullable|toExpr|setNullable|union|unionAll|intersect|except)\(|\.(asc|desc|isNull|isNotNull)\(\)|`(is|in|as)`|\.(isNot|greaterThan|greaterOrEqual|lessThan|lessOrEqual|notIn|notLike|glob|notGlob|between|notBetween)\(|@ViewQuery|CompiledSelect|Query\.Mapper|logQueryTime|OperationFailedException' <source roots>
   ```

   Expect false positives; the list scopes the work, the compiler confirms it. Also note every query that joins,
   aliases, divides, aggregates, counts, nests a query, reads a view through raw SQL or catches entity-operation
   failures: those are the behavior checklist's sites.
3. The rewritten DSL targets Kotlin. Port Java code that builds queries to Kotlin rather than writing Java against
   the new stages; Java models stay supported if they are public.

## Migrate in two passes

**Pass 1, spelling.** Rewrite each site with the spelling references, file by file, compiling often. The rules
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
- RxJava APIs come from the `com.siimkinks.sqlitemagic:sqlitemagic-rxjava2` artifact, at the plugin's version. Add it
  where code observes, and import `observe`, `run`, `scheduleRxQueriesOn` and the observable types from
  `com.siimkinks.sqlitemagic.rxjava2`. Modules that declared RxJava only because runtime signatures used it can drop it.
  A project on RxJava 3 uses `sqlitemagic-rxjava3` and `com.siimkinks.sqlitemagic.rxjava3`, which have the same API.
  Coroutine code uses `sqlitemagic-coroutines`: `observe()` returns a `Flow`, and one-shot writes use suspend `await()`.
- Setup takes a driver: `sqliteFactory(FrameworkSQLiteOpenHelperFactory())` becomes `driver(FrameworkSQLiteDriver())`
  or `driver(BundledSQLiteDriver())`, and `Cursor` reads become `useRows`; see
  [statements-and-types.md](references/statements-and-types.md#setup-drivers-and-reading-rows).

Do not recreate legacy names with local extension functions, wrappers or typealiases to shrink the diff. They keep
legacy semantics alive in the caller's head while the engine behaves differently, and they hide the sites the
behavior pass must review.

**Pass 2, behavior.** Walk [behavior.md](references/behavior.md) for every migrated query, not only the ones that
needed edits: several changes apply to code whose spelling is unchanged. Fix the cause the checklist names (lift a
field with `.nullable()`, select a required property, join explicitly, choose `SUM` or `TOTAL`, catch
`SQLException`) instead of silencing it with `!!`, broad catches or `readAs` casts. When an item needs a product
decision, such as whether a result should keep legacy semantics, stop and ask with the concrete before/after.

## Verify

- Compile every migrated source set, including tests, after regeneration.
- Run the tests covering migrated queries. Rejections before SQL, changed values and changed exceptions only show at
  runtime, preferably against a real SQLite database (Android device or emulator, or a JVM SQLite driver). Add a
  focused test where a behavior item applies and nothing covers it.
- In the SqliteMagic repository, follow the maintainers' local agent guidance and DSL testing skill for selectors,
  publication and Android runs; pre-migration infix calls stay infix there.

## Report

List the migrated files, the behavior items that applied and how each was resolved, decisions left to the user, and
which runtime behavior no test exercised. Do not claim behavior parity that only compilation established.

## Keep the references current

When the repository's breaking-changes log gains an implemented change that alters what a legacy consumer writes or
observes, update the matching reference in the same change. Skip changes between unreleased rewrite states and
changes that only alter generated SQL text.
