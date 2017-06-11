package com.siimkinks.sqlitemagic.validator;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.FieldColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import lombok.NoArgsConstructor;

import static com.siimkinks.sqlitemagic.element.FieldColumnElement.useAccessMethods;
import static javax.lang.model.element.Modifier.ABSTRACT;

@Singleton
public class ModelValidator {

  public static final String ERR_TABLE_MISPLACEMENT = String.format("Only classes can be annotated with @%s", Table.class.getSimpleName());
  public static final String ERR_MISSING_COLUMNS = "Table objects must have at least one column";
  public static final String ERR_VALUE_ELEMENT_MISSING_ID_COLUMN = String.format("@%s annotated objects must have @%s annotated column", Table.class.getSimpleName(), Id.class.getSimpleName());
  public static final String ERR_COLUMN_ANNOTATION_MISPLACEMENT = String.format("@%s annotation is misplaced", Column.class.getSimpleName());
  public static final String ERR_ID_COLUMN_WRONG_TYPE = String.format("@%s must be either %s or %s", Id.class.getSimpleName(), Long.class.getName(), long.class.getName());
  public static final String ERR_MISSING_NO_ARGS_CONSTRUCTOR = String.format("Mutable classes with @%s annotation must have no args constructor", Table.class.getSimpleName());
  private final Environment environment;

  @Inject
  public ModelValidator(Environment environment) {
    this.environment = environment;
  }

  public boolean isTableElementValid(TableElement tableElement) {
    final TypeElement rawElement = tableElement.getTableElement();
    if (rawElement.getKind() != ElementKind.CLASS) {
      environment.error(rawElement, ERR_TABLE_MISPLACEMENT);
      return false;
    }
    if (rawElement.getModifiers().contains(ABSTRACT) && !environment.hasAutoValueLib()) {
      environment.error(rawElement, "%s is an abstract class, but project is missing configured AutoValue library [%s] " +
              "and abstract classes by themselves are not supported as table objects",
          rawElement.getSimpleName().toString(),
          environment.getAutoValueAnnotationQualifiedName());
      return false;
    }
    if (tableElement.getAllColumns().isEmpty()) {
      environment.error(rawElement, ERR_MISSING_COLUMNS);
      return false;
    }
    if (tableElement.isImmutable()) {
      return isImmutableTableElementValid(tableElement, rawElement);
    }
    return isRegularTableElementValid(rawElement);
  }

  private boolean isImmutableTableElementValid(TableElement tableElement, TypeElement rawElement) {
    if (!tableElement.hasId()) {
      environment.error(rawElement, ERR_VALUE_ELEMENT_MISSING_ID_COLUMN);
      return false;
    }
    return true;
  }

  private boolean isRegularTableElementValid(TypeElement rawElement) {
    boolean hasNoArgsConstructor = rawElement.getAnnotation(NoArgsConstructor.class) != null;
    if (!hasNoArgsConstructor) {
      for (Element enclosedElement : rawElement.getEnclosedElements()) {
        if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
          ExecutableElement constructor = (ExecutableElement) enclosedElement;
          if (Utils.isEmpty(constructor.getParameters())) {
            hasNoArgsConstructor = true;
            break;
          }
        }
      }
    }
    if (!hasNoArgsConstructor) {
      environment.error(rawElement, ERR_MISSING_NO_ARGS_CONSTRUCTOR);
      return false;
    }
    return true;
  }

  public boolean isColumnElementValid(Element rawElement, ColumnElement columnElement, TableElement tableElement) {
    if (columnElement == null) {
      environment.error(tableElement.getTableElement(), ERR_COLUMN_ANNOTATION_MISPLACEMENT);
      return false;
    }
    ExtendedTypeElement typedColumnElement = columnElement.getDeserializedType();
    if (!Const.SQL_TYPE_MAP.containsKey(typedColumnElement.getQualifiedName())
        && !environment.hasTransformerFor(typedColumnElement)
        && columnElement.isHandledRecursively() && !columnElement.hasReferencedTable()) {
      environment.error(rawElement, "Field \"%s\" class %s is not any of sql types: [%s] and does not have transformer!\n" +
              "Implement transformer or change column type or add @%s annotation to referenced object",
          rawElement.getSimpleName(), typedColumnElement.getQualifiedName(),
          Joiner.on(", ").join(Const.SQL_TYPE_MAP.keySet()),
          Table.class.getSimpleName()
      );
      return false;
    }
    if (Strings.isNullOrEmpty(columnElement.getGetterString())) {
      environment.error(rawElement, "Missing access method");
      return false;
    }
    if (columnElement.isId()) {
      ExtendedTypeElement deserializedType = columnElement.getDeserializedType();
      if (!environment.getTypeUtils().isSameType(deserializedType.getTypeElement().asType(), Const.LONG_TYPE)) {
        environment.error(rawElement, ERR_ID_COLUMN_WRONG_TYPE);
        return false;
      }
    }
    if (columnElement.isReferencedColumn()
        && !columnElement.isHandledRecursively()
        && columnElement.isReferencedTableImmutable()
        && columnElement.getReferencedTable().hasAnyNonIdNotNullableColumns()) {
      environment.error(rawElement, "Referenced column must be handled recursively - immutable object includes non ID fields which are not nullable");
      return false;
    }
    if (!tableElement.isImmutable()
        && columnElement instanceof FieldColumnElement
        && ((FieldColumnElement) columnElement).getModifiers().contains(Modifier.PRIVATE)
        && !useAccessMethods(columnElement.getColumnAnnotation(), tableElement)) {
      environment.error(rawElement, "Private field column must be declared to use access methods. Set @%s(useAccessMethods = true)", Column.class.getSimpleName());
      return false;
    }
    return true;
  }
}
