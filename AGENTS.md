## Code Style

- Use spaces for indentation in every generated edit. Do not insert tab characters in source files.
- Use 2 spaces for normal indentation and 4 spaces for continuation indentation.
- Before finishing a code change, check that any newly generated or modified lines follow these spacing rules and normalize them if needed.
- When a function call uses more than one argument, use named arguments.
- Exception: for Kotlin vararg function calls, use positional values. For static collection contents, prefer direct factories (`listOf`, `setOf`, `mapOf`, `arrayOf`, etc.) instead of builders.
- Always use callable references whenever possible instead of equivalent lambdas.
  This includes property references and function references in collection operations and nullable chains, for example
  `map(Foo::bar)`, `mapNotNull(KClass<*>::qualifiedName)`, `filterIsInstance<Foo>()`, and `mapNotNull(::resolveFoo)` instead of
  `{ foo -> foo.bar }`, `{ foo -> resolveFoo(foo) }`, or `{ foo -> foo as? Foo }`.
- In Kotlin, omit explicit types when they can be inferred without hurting clarity.
- In Kotlin, use explicit backing fields when a property should expose a read-only type while retaining a mutable implementation. Do not create a separate `mutable...` property solely for backing storage when this can be expressed as, for example, `val items: List<Item> field = mutableListOf()`.
- In Kotlin, prefer expression bodies whenever possible.
- In Kotlin, prefer `when` over `if` whenever possible, especially when selecting between multiple conditions or producing an expression value.
- When a function call uses more than one named argument, put every argument on its own line, including the first argument, and keep the closing `)` on its own line.
- When more than one function call is chained, put each call on its own line.
- Exception for chained calls: if the receiver expression before the first call is very short and the call's argument list fits on one line, the first call may stay on the same line as the receiver; otherwise start the first call on a new line as well.
- When a chained call has its arguments expanded across multiple lines, always start that call on a new line regardless of receiver length, so that subsequent chained calls align with the leading dot rather than the closing `)`.

Examples:

```kotlin
// short receiver, single-line args — first call stays on same line
whenever(it.search(any())).thenReturn(Single.fromCallable { response })

// any receiver, expanded args — first call on new line
gateway
    .searchTransactionsForAccount(
        accountId = accountId,
        keyword = keyword
    )
    .test()
    .assertResult(QueryResult.Success)
```

## Testing

- When testing data classes, assert the full data class value instead of asserting each property separately.
- Prefer constructing the expected instance and comparing it to the actual result in a single assertion.
- Keep single-use test fixtures inline. Extract a fixture into a reusable function if and only if it is used more than once.
