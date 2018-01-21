package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define a submodule database configuration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface SubmoduleDatabase {
  /**
   * Unique name for classifying submodule database configuration.
   *
   * @return Submodule name
   */
  String value();
}
