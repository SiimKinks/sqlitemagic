package com.siimkinks.sqlitemagic.element;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.typeNameForGenerics;
import static com.siimkinks.sqlitemagic.element.ColumnElement.findEquivalentType;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ViewColumnElement implements BaseColumnElement {

  private final Environment environment;
  private final Element element;
  private final String columnName;
  private final boolean complex;
  private final String methodName;
  private final String getterString;
  private final ExtendedTypeElement serializedType;
  private final TableElement referencedTable;
  private final boolean nullable;
  private final TransformerElement transformer;
  private final ExtendedTypeElement deserializedType;
  private final TypeName viewColumnTypeName;

  public static ViewColumnElement create(Environment environment,
                                         Element element,
                                         @Nullable ViewColumn annotation) {
    final String annotationValue = annotation != null ? annotation.value() : null;
    final String columnName;
    final TypeMirror type;
    if (element instanceof VariableElement) {
      final VariableElement e = (VariableElement) element;
      type = e.asType();
      columnName = FieldColumnElement.determineColumnName(e, annotationValue);
    } else if (element instanceof ExecutableElement) {
      final ExecutableElement e = (ExecutableElement) element;
      type = e.getReturnType();
      columnName = MethodColumnElement.determineColumnName(e, annotationValue);
    } else {
      throw new IllegalStateException("Unknown view column element type");
    }

    final ExtendedTypeElement deserializedType = environment.getAnyTypeElement(type);
    final TableElement referencedTable = environment.getTableElementFor(deserializedType.getQualifiedName());
    final boolean allFromTable = referencedTable != null;
    final TransformerElement transformer = environment.getTransformerFor(deserializedType);
    final String methodName = element.getSimpleName().toString();
    final ExtendedTypeElement serializedType = ColumnElement.getSerializedType(environment, deserializedType, transformer, referencedTable);

    return builder()
        .environment(environment)
        .element(element)
        .columnName(columnName)
        .complex(allFromTable)
        .methodName(methodName)
        .getterString(methodName + "()")
        .serializedType(serializedType)
        .referencedTable(referencedTable)
        .nullable(determineNullability(element))
        .transformer(transformer)
        .deserializedType(deserializedType)
        .viewColumnTypeName(TypeName.get(type))
        .build();
  }

  private static boolean determineNullability(Element element) {
    return WriterUtil.hasNullableAnnotation(element);
  }

  @Override
  public String getElementName() {
    return methodName;
  }

  @Override
  public String getGetterString() {
    return getterString;
  }

  @Override
  public boolean hasNullableAnnotation() {
    return nullable;
  }

  @Override
  public boolean hasTransformer() {
    return getTransformer() != null;
  }

  @Override
  public boolean isReferencedColumn() {
    return complex;
  }

  public ExtendedTypeElement getSerializedType() {
    return ColumnElement.getSerializedType(getEnvironment(), getDeserializedType(),
        getTransformer(), getReferencedTable());
  }

  @Override
  public TypeName getDeserializedTypeNameForGenerics() {
    return typeNameForGenerics(getDeserializedType());
  }

  @Override
  public TypeName getSerializedTypeNameForGenerics() {
    return typeNameForGenerics(getSerializedType());
  }

  @Override
  public TypeName getEquivalentType() {
    return findEquivalentType(getSerializedType(), getEnvironment());
  }

  @Override
  public String cursorGetter(String cursorVariable, String offsetString) {
    return ColumnElement.cursorGetter(getSerializedType(), cursorVariable, offsetString);
  }

  @Override
  @Nullable
  public String cursorParserConstantName(@NonNull Environment environment) {
    return Const.cursorParserConstantName(getSerializedType(), environment, isNullable());
  }

  @Override
  public boolean isId() {
    return false;
  }

  @Override
  public boolean isNumericType() {
    return Const.NUMERIC_SQL_TYPE_MAP.containsKey(getSerializedType().getQualifiedName());
  }

  @Override
  public boolean isUnique() {
    return false;
  }

  @Override
  public boolean isNeededForShallowQuery() {
    return complex && referencedTable.hasAnyNonIdNotNullableColumns();
  }

  @Override
  public boolean isHandledRecursively() {
    return isReferencedColumn();
  }

  @Override
  public FormatData deserializedValueSetter(String entityElementVariableName, String settableValue, String managerVariableName) {
    if (hasTransformer()) {
      return transformer.deserializedValueSetter(settableValue);
    } else if (getDeserializedType().isBoxedByteArray(getEnvironment())) {
      return FormatData.create("$T.toByteArray(" + settableValue + ")", Utils.class);
    }
    return FormatData.create(settableValue);
  }
}
