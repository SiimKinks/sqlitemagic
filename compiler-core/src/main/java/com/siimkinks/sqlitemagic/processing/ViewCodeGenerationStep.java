package com.siimkinks.sqlitemagic.processing;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.writer.ViewWriter;

import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

public final class ViewCodeGenerationStep implements ProcessingStep {

  @Inject
  Environment environment;
  private final Filer filer;
  @Inject
  ViewWriter viewWriter;

  public ViewCodeGenerationStep() {
    BaseProcessor.inject(this);
    this.filer = environment.getFiler();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (ViewElement viewElement : environment.getViewElements()) {
      TypeElement element = viewElement.getViewElement();
      try {
        viewWriter.writeSource(filer, viewElement);
      } catch (Exception e) {
        final String errMsg = e.getMessage();
        environment.error(element, errMsg);
        if (Strings.isNullOrEmpty(errMsg)) {
          e.printStackTrace();
        }
        return false;
      }
    }
    return true;
  }
}
