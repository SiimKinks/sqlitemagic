package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define a database configuration.
 * <p>
 * Using this annotation to define a database is optional.<br>
 * It must be used when database configuration is defined across many modules.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Database {
  /**
   * @return Database name
   */
  String name() default "";

  /**
   * @return Database version
   */
  int version() default -1;

  /**
   * Submodules database configurations.
   * Each class in the list must be annotated with {@link SubmoduleDatabase}
   *
   * @return List of submodules database configurations.
   */
  Class<?>[] submodules() default {};
}
