package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Marks field value to be {@link View} defining query.
 * </p>
 * Each view must only have one query.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface ViewQuery {
}
