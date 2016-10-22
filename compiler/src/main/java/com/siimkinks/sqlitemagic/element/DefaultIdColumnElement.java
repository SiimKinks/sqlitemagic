package com.siimkinks.sqlitemagic.element;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.TypeName;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Modifier;

import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.siimkinks.sqlitemagic.Const.DEFAULT_ID_COLUMN_NAME;
import static com.siimkinks.sqlitemagic.Const.DEFAULT_ID_FIELD_NAME;
import static com.siimkinks.sqlitemagic.Const.DEFAULT_ID_SCHEMA;

/**
 * @author Siim Kinks
 */
@Data
@EqualsAndHashCode(of = {"columnName"}, callSuper = false)
public final class DefaultIdColumnElement extends ColumnElement {

  private final TypeName typeName = TypeName.LONG;
  private final Environment environment;
  private final TableElement enclosingTable;
  private final String columnName = DEFAULT_ID_COLUMN_NAME;
  private final String schema = DEFAULT_ID_SCHEMA;
  private final String elementName = DEFAULT_ID_FIELD_NAME;
  private final String sqlType = "INTEGER";
  private final String getterString = "id";
  private final TableElement referencedTable = null;
  private final Id idAnnotation = null;
  private final Unique uniqueAnnotation = null;
  private final Column columnAnnotation = null;
  private final TransformerElement transformer = null;
  private final boolean id = true;
  private final boolean autoincrementId = true;
  private final boolean handledRecursively = false;
  private final boolean referencedColumn = false;
  private final boolean nullable = false;

  // package private
  private final Set<Modifier> modifiers = new HashSet<>();

  public static ColumnElement get(Environment environment, TableElement enclosingTable) {
    return new DefaultIdColumnElement(environment, enclosingTable);
  }

  private DefaultIdColumnElement(Environment environment, TableElement enclosingTable) {
    this.environment = environment;
    this.enclosingTable = enclosingTable;
  }

  @Override
  public String valueGetter(String entityElementVariableName) {
    return defaultIdAccess(entityElementVariableName);
  }

  @Override
  public FormatData serializedValueGetterFromEntity(String entityElementVariableName) {
    return FormatData.create(valueGetter(entityElementVariableName));
  }

  @Override
  public String valueSetter(String entityElementVariableName, String settableValue) {
    return defaultIdAccess(entityElementVariableName) + " = " + settableValue;
  }

  @Override
  public FormatData deserializedValueSetter(String entityElementVariableName, String settableValue, String managerVariableName) {
    return FormatData.create(valueSetter(entityElementVariableName, settableValue));
  }

  @Override
  public ExtendedTypeElement getSerializedType() {
    return getDeserializedType();
  }

  // TODO review this
  @Override
  public boolean hasNullableAnnotation() {
    return false;
  }

  @Override
  public ExtendedTypeElement getDeserializedType() {
    return ExtendedTypeElement.PRIMITIVE_LONG(environment);
  }

  @Override
  public String cursorGetter(String cursorVariable, String offsetString) {
    return cursorGetter(this, cursorVariable, offsetString);
  }

  private String defaultIdAccess(String tableElementVariableName) {
    if (!Strings.isNullOrEmpty(tableElementVariableName)) {
      return String.format("%s.%s", tableElementVariableName, DEFAULT_ID_FIELD_NAME);
    }
    return getGetterString();
  }
}
