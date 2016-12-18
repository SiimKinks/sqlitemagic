Change Log
========

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
