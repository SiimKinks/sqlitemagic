package com.siimkinks.sqlitemagic.processing;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.writer.ModelWriter;

import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

public class ModelCodeGenerationStep implements ProcessingStep {

  @Inject
  Environment environment;
  private final Filer filer;
  @Inject
  ModelWriter modelWriter;

  public ModelCodeGenerationStep() {
    BaseProcessor.inject(this);
    this.filer = environment.getFiler();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TableElement tableElement : environment.getTableElements()) {
      TypeElement element = tableElement.getTableElement();
      try {
        modelWriter.writeSource(filer, tableElement);
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
