package com.siimkinks.sqlitemagic.annotation.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks some method to be the invoker of some other method defined in {@link #value()}.
 * <p>
 * By default all parameters defined by the annotated method will be the input for invoked
 * method. When annotated method has some return type it is expected that the invoked
 * method will return the same type. When annotated method does not return anything then
 * the return value of the annotated method is ignored.
 * <p>
 * Invocation target must be in the format of {@code <fully qualified class name>#<method name>}
 * <p>
 * <b>Example:</b><br>
 * {@code com.siimkinks.sqlitemagic.SqlUtil#getNrOfTables}
 * <p>
 * For internal use!
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Invokes {
  /**
   * Invocation target in the format of {@code <fully qualified class name>#<method name>}
   * <p>
   * <b>Example:</b><br>
   * {@code com.siimkinks.sqlitemagic.SqlUtil#getNrOfTables}
   *
   * @return Invocation target
   */
  String value();

  /**
   * Define if invoked method should receive "{@code this}" as the only input parameter.
   *
   * @return {@code true} if invoked method should receive "{@code this}" as the only
   * input parameter; {@code false} if invoked method should receive all annotated method
   * parameters as input.
   */
  boolean useThisAsOnlyParam() default false;
}
