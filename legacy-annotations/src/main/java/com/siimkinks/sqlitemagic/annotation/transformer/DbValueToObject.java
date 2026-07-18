package com.siimkinks.sqlitemagic.annotation.transformer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that method transforms database retrieved raw object to Java object.<br>
 * Marked method must be static.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface DbValueToObject {
}
