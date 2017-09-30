package com.siimkinks.sqlitemagic.annotation.transformer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that class acts as the transformer between Java objects and database compatible primitive
 * objects.
 *
 * @deprecated No need to use this annotation any more.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Deprecated
public @interface Transformer {
}
