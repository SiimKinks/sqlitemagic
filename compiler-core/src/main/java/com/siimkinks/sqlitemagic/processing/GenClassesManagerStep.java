package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.IndexElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.validator.TransformerValidator;
import com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

import lombok.Getter;

public class GenClassesManagerStep implements ProcessingStep {

  @Inject
  Environment environment;
  @Inject
  GenClassesManagerWriter writer;
  @Inject
  TransformerValidator transformerValidator;
  @Getter
  private final Map<String, TransformerElement> allTransformerElements = new HashMap<>();
  @Getter
  private final List<ViewElement> allViewElements = new ArrayList<>();
  @Getter
  private final List<IndexElement> allIndexElements = new ArrayList<>();

  public GenClassesManagerStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      try {
        writer.writeSource(environment, this);
      } catch (IOException e) {
        e.printStackTrace();
        return false;
      }
    } else {
      collectGeneratedData();
    }
    return true;
  }

  private void collectGeneratedData() {
    allViewElements.addAll(environment.getViewElements());
    allIndexElements.addAll(environment.getIndexElements());
    allTransformerElements.putAll(environment.getTransformerElements());
  }
}
