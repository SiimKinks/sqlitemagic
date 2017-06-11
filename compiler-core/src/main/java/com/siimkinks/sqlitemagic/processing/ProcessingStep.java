package com.siimkinks.sqlitemagic.processing;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

public interface ProcessingStep {
  boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);
}
