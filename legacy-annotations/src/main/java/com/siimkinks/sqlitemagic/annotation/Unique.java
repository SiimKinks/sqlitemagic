package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define a UNIQUE constraint on column.
 * <p>
 * A single table may have any number of UNIQUE constraints. For each UNIQUE constraint
 * on the table, each row must contain a unique combination of values in the columns
 * identified by the UNIQUE constraint. For the purposes of UNIQUE constraints,
 * NULL values are considered distinct from all other values, including other NULLs.
 *
 * @see <a href="https://www.sqlite.org/lang_createtable.html">SQLite documentation: CREATE TABLE</a>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Unique {
}
