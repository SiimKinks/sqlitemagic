package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define a column in table.<br>
 * This annotation is valid on methods for value objects and on fields for non-value
 * objects.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Column {

	/**
	 * Column name.<br>
	 * Defaults to field or method name where camel case is replaced with "_".
	 *
	 * @return Column name.
	 */
	String value() default "";

	/**
	 * Define if non-primitive field should be handled recursively, meaning that its values
	 * are persisted/retrieved in full extent. If set to false, only its {@link Id} annotated
	 * field is persisted/retrieved.<br>
	 * Note that non-primitive fields must also be defined with {@link Table} annotation for system
	 * to know how to persist/retrieve them.
	 *
	 * @return True if complex objects should be handled recursively, false if only {@link Id} fields
	 * are handled.
	 */
	boolean handleRecursively() default true;

	/**
	 * Define for non-primitive field if ON DELETE CASCADE foreign key action should be added to
	 * table definition.
	 *
	 * @return True if ON DELETE CASCADE foreign key action should be added to table definition, false if not.
	 */
	boolean onDeleteCascade() default false;

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
