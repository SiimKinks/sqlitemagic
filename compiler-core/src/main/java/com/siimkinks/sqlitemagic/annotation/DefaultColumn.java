package com.siimkinks.sqlitemagic.annotation;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;

public class DefaultColumn implements Column {
  public static Column INSTANCE = new DefaultColumn();

  private DefaultColumn() {
  }

  @Override
  public String value() {
    return "";
  }

  @Override
  public String defaultValue() {
    return "";
  }

  @Override
  public boolean handleRecursively() {
    return true;
  }

  @Override
  public boolean onDeleteCascade() {
    return false;
  }

  @Override
  public boolean useAccessMethods() {
    return false;
  }

  @Override
  public String belongsToIndex() {
    return "";
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return Column.class;
  }

  public static Column getColumnOrDefaultIfMissing(Element element) {
    Column columnAnnotation = element.getAnnotation(Column.class);
    if (columnAnnotation != null) {
      return columnAnnotation;
    }
    return INSTANCE;
  }
}
