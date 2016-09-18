package com.siimkinks.sqlitemagic.validator;

import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.element.ViewElement;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;

import static com.siimkinks.sqlitemagic.Const.COMPILED_SELECT;

@Singleton
public final class ViewValidator {
	private static final String ERR_MULTI_QUERY = "Views can have only one query";
	private static final String ERR_WRONG_QUERY_TYPE = String.format("View query must be instance of %s", CompiledSelect.class.getSimpleName());
	public static final String ERR_WRONG_TYPE = String.format("@%s must be on either interface or value class (abstract class that is annotated with @%%s)",
			View.class.getSimpleName());
	private static final String ERR_MISSING_QUERY = String.format(" view must have static query field which is an instance of %s and annotated with @%s",
			CompiledSelect.class.getSimpleName(), ViewQuery.class.getSimpleName());

	private final Environment environment;

	@Inject
	public ViewValidator(Environment environment) {
		this.environment = environment;
	}

	public boolean isViewElementValid(ViewElement viewElement) {
		if (viewElement.getAllColumnsCount() == 0) {
			environment.error(viewElement.getViewElement(), "Found no view columns in view class \"%s\". Each view must define at least one method with @%s annotation",
					viewElement.getViewElementName(),
					ViewColumn.class.getSimpleName());
			return false;
		}
		if (viewElement.isMultipleQueries()) {
			environment.error(viewElement.getViewElement(), ERR_MULTI_QUERY);
			return false;
		}
		if (viewElement.getQueryConstant() == null) {
			environment.error(viewElement.getViewElement(), viewElement.getViewElementName() + ERR_MISSING_QUERY);
			return false;
		}
		final Types typeUtils = environment.getTypeUtils();
		if (!typeUtils.isAssignable(COMPILED_SELECT, viewElement.getQueryConstant().asType())) {
			environment.error(viewElement.getViewElement(), ERR_WRONG_QUERY_TYPE);
			return false;
		}
		final TypeElement rawElement = viewElement.getViewElement();
		if (!viewElement.isInterface()) {
			final Set<Modifier> modifiers = rawElement.getModifiers();
			final Class<? extends Annotation> autoValueAnnotation = environment.getAutoValueAnnotation();
			if (!modifiers.contains(Modifier.ABSTRACT) || rawElement.getAnnotation(autoValueAnnotation) == null) {
				environment.error(rawElement, String.format(ERR_WRONG_TYPE, autoValueAnnotation.getSimpleName()));
				return false;
			}
		}
		return true;
	}
}
