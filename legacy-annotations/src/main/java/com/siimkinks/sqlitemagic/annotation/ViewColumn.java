package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Define {@link View} column details.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface ViewColumn {
  /**
   * <p>Fully qualified column name or column alias.</p>
   * Examples:<br>
   * <ul>
   * <li>fully qualified name -- "author.name"</li>
   * <li>alias -- "foo"</li>
   * </ul>
   *
   * @return Column name or alias.
   */
  String value();
}
