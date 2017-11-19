package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.TypeName;

public interface BaseColumnElement {
  String getElementName();

  String getGetterString();

  String getColumnName();

  TableElement getReferencedTable();

  ExtendedTypeElement getDeserializedType();

  TypeName getDeserializedTypeNameForGenerics();

  TypeName getSerializedTypeNameForGenerics();

  TypeName getEquivalentType();

  String cursorGetter(String cursorVariable, String offsetString);

  @Nullable
  String cursorParserConstantName(@NonNull Environment environment);

  TransformerElement getTransformer();

  boolean isId();

  boolean hasTransformer();

  boolean isReferencedColumn();

  boolean isNeededForShallowQuery();

  boolean isHandledRecursively();

  boolean isNumericType();

  boolean isNullable();

  boolean isUnique();

  boolean hasNullableAnnotation();

  FormatData deserializedValueSetter(String entityElementVariableName, String settableValue, String managerVariableName);
}
