Change Log
==========

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
