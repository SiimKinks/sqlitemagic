package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * All fields with this annotation are ignored when {@link Table#persistAll()} is true on
 * {@link Table} annotation.<br>
 * It has no other functionality.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface IgnoreColumn {
}
