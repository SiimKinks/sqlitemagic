package com.siimkinks.sqlitemagic.processing;

import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.DefaultColumn;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.FieldColumnElement;
import com.siimkinks.sqlitemagic.element.MethodColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.exception.DuplicateException;
import com.siimkinks.sqlitemagic.validator.ModelValidator;
import com.siimkinks.sqlitemagic.validator.StrongComponentsFinder;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public class ModelCollectionStep implements ProcessingStep {
  @Inject
  Environment environment;
  @Inject
  ModelValidator validator;

  public ModelCollectionStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean isSuccessfulProcess = true;
    final Set<? extends Element> tableElements = roundEnv.getElementsAnnotatedWith(Table.class);
    if (!tableElements.isEmpty()) {
      for (Element element : tableElements) {
        try {
          TableElement tableElement = new TableElement(environment, element);
          environment.addTableElement(tableElement);
        } catch (Exception e) {
          final String errMsg = e.getMessage();
          environment.error(element, "Table collection error = " + errMsg);
          if (errMsg == null) {
            e.printStackTrace();
          }
          return false;
        }
      }
      for (TableElement collectedTable : environment.getTableElements()) {
        Element element = collectedTable.getTableElement();
        try {
          if (!collectColumns(element, collectedTable)) {
            isSuccessfulProcess = false;
            continue;
          }
          collectedTable.finishCreate();
          if (!validator.isTableElementValid(collectedTable)) {
            isSuccessfulProcess = false;
          }
        } catch (Exception e) {
          final String errMsg = e.getMessage();
          environment.error(element, "Table column collection error [%s|message=%s]", e.getClass().getSimpleName(), errMsg);
          if (errMsg == null) {
            e.printStackTrace();
          }
          return false;
        }
      }
      for (TableElement collectedTable : environment.getTableElements()) {
        collectedTable.collectColumnsMetadata();
      }
      final StrongComponentsFinder strongComponentsFinder = new StrongComponentsFinder(environment.getAllTableElements(), environment.getAllTableNames());
      if (strongComponentsFinder.strongComponentsCount() > 0) {
        strongComponentsFinder.printStrongComponents(environment.getMessager());
        return false;
      }
    }
    return isSuccessfulProcess;
  }

  private boolean collectColumns(Element parentElement, TableElement tableElement) throws DuplicateException {
    if (tableElement.isImmutable()) {
      final ImmutableSet<ExecutableElement> allMethods = environment.getLocalAndInheritedColumnMethods((TypeElement) parentElement);
      tableElement.setAllMethods(allMethods);
      return collectMethodColumns(tableElement, allMethods);
    } else {
      final ImmutableSet<VariableElement> allFields = environment.getLocalAndInheritedColumnFields((TypeElement) parentElement);
      tableElement.setAllFields(allFields);
      return collectFieldColumns(tableElement, allFields);
    }
  }

  private boolean collectMethodColumns(TableElement tableElement, ImmutableSet<ExecutableElement> allMethods) throws DuplicateException {
    boolean isSuccessfulProcess = true;
    if (tableElement.persistAll()) {
      for (ExecutableElement method : allMethods) {
        if (method.getAnnotation(IgnoreColumn.class) != null) {
          continue;
        }
        final Column columnAnnotation = DefaultColumn.getColumnOrDefaultIfMissing(method);
        final ColumnElement columnElement = MethodColumnElement.create(environment, method, columnAnnotation, tableElement);
        isSuccessfulProcess = addColumn(tableElement, isSuccessfulProcess, method, columnElement);
      }
    } else {
      for (ExecutableElement method : allMethods) {
        final Column columnAnnotation = method.getAnnotation(Column.class);
        if (columnAnnotation == null) {
          continue;
        }
        final ColumnElement columnElement = MethodColumnElement.create(environment, method, columnAnnotation, tableElement);
        isSuccessfulProcess = addColumn(tableElement, isSuccessfulProcess, method, columnElement);
      }
    }
    return isSuccessfulProcess;
  }

  private boolean collectFieldColumns(TableElement tableElement, ImmutableSet<VariableElement> allFields) throws DuplicateException {
    boolean isSuccessfulProcess = true;
    if (tableElement.persistAll()) {
      for (VariableElement field : allFields) {
        if (field.getAnnotation(IgnoreColumn.class) != null) {
          continue;
        }
        final Column columnAnnotation = DefaultColumn.getColumnOrDefaultIfMissing(field);
        final ColumnElement columnElement = FieldColumnElement.create(environment, field, columnAnnotation, tableElement);
        isSuccessfulProcess = addColumn(tableElement, isSuccessfulProcess, field, columnElement);
      }
    } else {
      for (VariableElement field : allFields) {
        final Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation == null) {
          continue;
        }
        final ColumnElement columnElement = FieldColumnElement.create(environment, field, columnAnnotation, tableElement);
        isSuccessfulProcess = addColumn(tableElement, isSuccessfulProcess, field, columnElement);
      }
    }
    return isSuccessfulProcess;
  }

  private boolean addColumn(TableElement tableElement, boolean isSuccessfulProcess, Element enclosedElement, ColumnElement columnElement) throws DuplicateException {
    if (!validator.isColumnElementValid(enclosedElement, columnElement, tableElement)) {
      return false;
    }
    tableElement.addColumnElement(columnElement);
    return isSuccessfulProcess;
  }
}
