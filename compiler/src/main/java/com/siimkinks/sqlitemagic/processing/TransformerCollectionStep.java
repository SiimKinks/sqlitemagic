package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.SqliteMagicProcessor;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.validator.TransformerValidator;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class TransformerCollectionStep implements ProcessingStep {

	@Inject
	Environment environment;
	@Inject
	TransformerValidator validator;

	public TransformerCollectionStep() {
		SqliteMagicProcessor.inject(this);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		boolean isSuccessfulProcessing = true;
		for (Element element : roundEnv.getElementsAnnotatedWith(Transformer.class)) {
			TransformerElement transformerElement = new TransformerElement(environment, element);
			if (!validator.isTransformerValid(transformerElement)) {
				isSuccessfulProcessing = false;
			} else {
				environment.addTransformerElement(transformerElement);
			}
		}
		return isSuccessfulProcessing;
	}
}
