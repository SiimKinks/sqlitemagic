package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Index;
import com.siimkinks.sqlitemagic.element.IndexElement;
import com.siimkinks.sqlitemagic.validator.IndexValidator;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class IndexCollectionStep implements ProcessingStep {
  @Inject
  Environment environment;
  @Inject
  IndexValidator validator;

  public IndexCollectionStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean isSuccessfulProcess = true;
    final Set<? extends Element> indexElements = roundEnv.getElementsAnnotatedWith(Index.class);
    for (Element element : indexElements) {
      try {
        final IndexElement indexElement = new IndexElement(environment, element);
        if (validator.isIndexValid(indexElement)) {
          environment.addIndexElement(indexElement);
        } else {
          isSuccessfulProcess = false;
        }
      } catch (Exception e) {
        final String errMsg = e.getMessage();
        environment.error(element, "Index collection error = " + errMsg);
        if (errMsg == null) {
          e.printStackTrace();
        }
        return false;
      }
    }
    return isSuccessfulProcess;
  }
}
