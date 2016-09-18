package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.SqliteMagicProcessor;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.validator.ViewValidator;

import java.lang.annotation.AnnotationTypeMismatchException;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public final class ViewCollectionStep implements ProcessingStep {

	@Inject
	Environment environment;
	@Inject
	ViewValidator validator;

	public ViewCollectionStep() {
		SqliteMagicProcessor.inject(this);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		boolean isSuccessfulProcess = true;
		for (Element element : roundEnv.getElementsAnnotatedWith(View.class)) {
			try {
				final ViewElement viewElement = new ViewElement(environment, element);
				if (!validator.isViewElementValid(viewElement)) {
					isSuccessfulProcess = false;
				} else {
					environment.addViewElement(viewElement);
				}
			} catch (AnnotationTypeMismatchException ex) {
				environment.error(element, String.format("@%s and @%s annotation attribute values must be self defined constant expressions",
						View.class.getSimpleName(), ViewColumn.class.getSimpleName()));
				return false;
			} catch (Exception e) {
				environment.error(element, "View collection error = " + e.getMessage());
				e.printStackTrace();
				return false;
			}
		}

		return isSuccessfulProcess;
	}
}
