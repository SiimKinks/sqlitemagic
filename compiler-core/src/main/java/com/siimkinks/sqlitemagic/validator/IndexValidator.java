package com.siimkinks.sqlitemagic.validator;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.element.IndexElement;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@Singleton
public class IndexValidator {
  final Environment environment;

  @Inject
  public IndexValidator(Environment environment) {
    this.environment = environment;
  }

  public boolean isIndexValid(IndexElement indexElement) {
    boolean isValid = true;
    final Element indexTypeElement = indexElement.getIndexElement();
    final TypeElement tableTypeElement = indexElement.getTableTypeElement();
    if (tableTypeElement == null || tableTypeElement.getAnnotation(Table.class) == null) {
      environment.error(indexTypeElement,
          "Index must be defined inside or on @%s annotated class",
          Table.class.getSimpleName());
      isValid = false;
    }
    if (indexElement.isComposite() && indexElement.getIndexedColumnNames().isEmpty()) {
      environment.error(indexTypeElement, "Composite index must have at least one indexed column");
      isValid = false;
    }
    return isValid;
  }
}
