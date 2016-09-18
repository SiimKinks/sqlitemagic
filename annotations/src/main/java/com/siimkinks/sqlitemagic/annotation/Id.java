package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define some column to be the id for table.<br>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Id {
	/**
	 * Define if this id column should be auto-incremented or not.
	 *
	 * @return True if column is auto-incremented, false if not.
	 */
	boolean autoIncrement() default true;
}
