package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define an INDEX on table.
 * <p>
 * Adding an index usually speeds up your select queries but will slow down
 * other queries like insert or update. You should be careful when adding
 * indices to ensure that this additional cost is worth the gain.
 * <p>
 * Index can be defined on columns which creates a new index for specified column
 * or on table for creating a new composite index.
 * <p>
 * All columns that belong to a composite index must have {@link Column} annotation
 * with {@link Column#belongsToIndex()} parameter pointing to the defined composite
 * index name.
 *
 * @see <a href="https://www.sqlite.org/lang_createindex.html">SQLite documentation: CREATE INDEX</a>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Index {
  /**
   * Name of the index.
   * <p>
   * Defaults to the list of columns in the index joined by '_' and
   * prefixed by "index_${tableName}". So if you have a table with name "foo" and
   * a composite index of columns "bar" and "baz", the generated index name
   * will be "index_foo_bar_baz".
   *
   * @return The name of the index
   */
  String value() default "";

  /**
   * If set to true, this will be a unique index and any duplicates will be rejected.
   *
   * @return True if index is unique. False by default.
   */
  boolean unique() default false;
}
