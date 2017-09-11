package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.validator.TransformerValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

public class TransformerCollectionStep implements ProcessingStep {

  @Inject
  Environment environment;
  @Inject
  TransformerValidator validator;

  public TransformerCollectionStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean isSuccessfulProcessing = true;
    final Map<TypeMirror, TransformerElement> transformers = new HashMap<>();

    for (Element element : roundEnv.getElementsAnnotatedWith(ObjectToDbValue.class)) {
      final ExecutableElement objectToDbValue = (ExecutableElement) element;
      final TransformerElement transformerElement = TransformerElement.fromObjectToDbValue(environment, objectToDbValue);
      transformers.put(transformerElement.getRawDeserializedType(), transformerElement);
    }

    for (Element element : roundEnv.getElementsAnnotatedWith(DbValueToObject.class)) {
      final ExecutableElement dbValueToObject = (ExecutableElement) element;
      final TypeMirror rawDeserializedType = dbValueToObject.getReturnType();
      final TransformerElement transformer = transformers.get(rawDeserializedType);
      if (transformer != null) {
        transformer.addDbValueToObjectMethod(dbValueToObject);
      } else {
        final TransformerElement incompleteTransformer = TransformerElement.fromDbValueToObject(environment, dbValueToObject);
        transformers.put(incompleteTransformer.getRawDeserializedType(), incompleteTransformer);
      }
    }

    for (TransformerElement transformer : transformers.values()) {
      if (!validator.isTransformerValid(transformer)) {
        isSuccessfulProcessing = false;
      } else {
        environment.addTransformerElement(transformer);
      }
    }
    return isSuccessfulProcessing;
  }
}
