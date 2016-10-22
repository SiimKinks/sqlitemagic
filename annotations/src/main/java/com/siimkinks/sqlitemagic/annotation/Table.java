package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks class to be a table in SQLite database.<br>
 * <p>
 * If annotated class is
 * <p>
 * <b>Value object:</b><br>
 * {@link Column} annotations must be on methods<br>
 * Must have {@link Id} annotated method that returns long<br>
 * </p>
 * <p>
 * <b>Regular object:</b><br>
 * {@link Column} annotations must be on fields<br>
 * Optionally can have {@link Id} annotated long field. If missing, auto-incremented
 * id column with "_id" name is created along with package private long id field in
 * annotated model<br>
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Table {
  /**
   * Table name.<br>
   * Defaults to lower cased class name.
   *
   * @return Table name
   */
  String value() default "";

  /**
   * Persist all possible fields in object.<br>
   * If true system tries to create sql table columns
   * from all fields using all the default behavior of {@link Column}.
   * If any field has {@link Column} annotation then it uses its preferences.
   * If any field is annotated with {@link IgnoreColumn} then this field is ignored.
   *
   * @return True if all fields should be persisted, false if not.
   */
  boolean persistAll() default false;

  /**
   * Respect access methods when accessing model fields.<br>
   * Access methods can be with names representing field names e.g.
   * {@code name(), name(String)} or with JavaBeans-style prefixes like
   * {@code getName(), setName(String), isMale()}.<br>
   * This parameter is only applicable on non-value objects.
   *
   * @return True if system should access model fields with access methods, false if not.
   */
  boolean useAccessMethods() default false;
}
