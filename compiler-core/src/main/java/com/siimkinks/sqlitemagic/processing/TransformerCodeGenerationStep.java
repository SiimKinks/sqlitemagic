package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.writer.TransformerWriter;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

/**
 * @author Siim Kinks
 */
public class TransformerCodeGenerationStep implements ProcessingStep {

  @Inject
  Environment environment;
  @Inject
  TransformerWriter writer;

  public TransformerCodeGenerationStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (environment.get$processingRounds() == 1) {
      try {
        writer.writeSource(environment);
      } catch (IOException e) {
        e.printStackTrace();
        return false;
      }
    }
    return true;
  }
}
