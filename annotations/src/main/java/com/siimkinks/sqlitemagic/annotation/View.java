package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define view that corresponds to a <a href="https://www.sqlite.org/lang_createview.html">SQLite
 * view</a>.<br>
 * This annotation can only be used on interfaces or value objects.<br>
 * <p>
 * Each view must have one query.
 * View query must be defined in static field of type CompiledSelect and annotated
 * with {@link ViewQuery} annotation.
 * </p>
 * <p>
 * Each view must have {@link ViewColumn} annotated methods which are used to map view query to its columns..
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface View {
  /**
   * Represents view name.<br>
   * Defaults to lower case class name.
   *
   * @return View name.
   */
  String value() default "";
}
