Change Log
==========

Version 0.17.0 _(2018-01-06)_
---------------------------------

* Breaking: Rename `Select#val` to `Select#asColumn` as it was conflicting with kotlin reserved keywords
* New: Add support for raw UPDATE and DELETE statements
* Fix: Generic types parsing for transformers

Version 0.16.1 _(2017-11-28)_
---------------------------------

* Fix: Bugs related to support for object(s) update/persist operations with custom WHERE clause by unique column

Version 0.16.0 _(2017-11-19)_
---------------------------------

* New: Add nullability metadata to column types
    - Now every column has metadata about their nullability which makes the whole API even more type safe
* New: Add support for object(s) update/persist operations with custom WHERE clause by unique column
* New: Add `unaryMinus` method to numeric columns which changes positive values to negative and vice versa
* New: Add `not` method to expressions which negates the expression.

Version 0.15.2 _(2017-10-21)_
---------------------------------

* Fix: Transformation bug in Windows

Version 0.15.0 _(2017-09-30)_
---------------------------------

* New: Add support for Android Gradle plugin 3.0.0

Version 0.14.0 _(2017-09-11)_
---------------------------------

* New: All operations now support constraint conflict resolution
* New: Add support for multiple object transformers in one class
* New: More options to configure gradle plugin
* Fix: Nullable single column queries

Version 0.13.0 _(2017-06-25)_
---------------------------------

* New: Kotlin extensions module - adds useful extension functions and LINQ style SQL DSL
* New: Add ability to create raw queries without providing observed table(s)
* New: Add `DbConnection#clearData` method which clears all data in tables
* Fix: Generate correct code for complex data classes
* Fix: Do not fail bytecode transformation when IOE happens during class file loading

Version 0.12.0 _(2017-06-14)_
---------------------------------

* Better kotlin support (support data classes, generate extension functions instead of magic functions).

#### Breaking changes:

* Port to RxJava 2 (RxJava 1 support coming back in future release).

Version 0.11.0 _(2016-12-18)_
---------------------------------

* Option to create expression from raw string via `Expr.raw()`.
* Option to update complex column by its ID in `UPDATE` statement builder.
* Inner selection and function columns now append less SQL when possible.

#### Breaking changes:

* Return `Completable` instead of `Single<Boolean>` in entity bulk operations and `entity.update().observe()`

Version 0.10.0 _(2016-11-24)_
---------------------------------
_First public release_

* Better API for the [`ORDER BY` clause](https://github.com/SiimKinks/sqlitemagic/wiki/The-ORDER-BY-Clause).
* Unified naming in SQL. _Breaking change:_ in table names camelCase is replaced with underscores "_".
* Better wording in javadoc.
