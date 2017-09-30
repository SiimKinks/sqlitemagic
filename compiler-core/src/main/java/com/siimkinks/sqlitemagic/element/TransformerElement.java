package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.StringUtil;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import lombok.Getter;
import lombok.ToString;

import static com.siimkinks.sqlitemagic.WriterUtil.typeNameForGenerics;

@ToString
public class TransformerElement {
  private final Environment environment;

  private TypeElement deserializedTypeTransformerElement;
  private ClassName deserializedTypeTransformerClassName;
  private TypeElement serializedTypeTransformerElement;
  private ClassName serializedTypeTransformerClassName;
  @Getter
  private ExtendedTypeElement deserializedType;
  @Getter
  private TypeMirror rawDeserializedType;
  @Getter
  private ExtendedTypeElement serializedType;
  @Getter
  private TypeMirror rawSerializedType;
  @Getter
  private ExecutableElement objectToDbValueMethod;
  @Getter
  private ExecutableElement dbValueToObjectMethod;
  private boolean cannotTransformNullValues = false;

  private boolean hasObjectToDb = false;
  private boolean hasDbToObject = false;

  public TransformerElement(Environment environment) {
    this.environment = environment;
  }

  public static TransformerElement fromObjectToDbValue(Environment environment,
                                                       ExecutableElement objectToDbValue) {
    final TransformerElement transformer = new TransformerElement(environment);
    transformer.addObjectToDbValueMethod(objectToDbValue);
    return transformer;
  }

  public static TransformerElement fromDbValueToObject(Environment environment,
                                                       ExecutableElement dbValueToObject) {
    final TransformerElement transformer = new TransformerElement(environment);
    transformer.addDbValueToObjectMethod(dbValueToObject);
    return transformer;
  }

  public void addObjectToDbValueMethod(ExecutableElement method) {
    final TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();

    this.objectToDbValueMethod = method;
    this.serializedTypeTransformerElement = enclosingElement;
    this.serializedTypeTransformerClassName = ClassName.get(enclosingElement);

    if (!hasDbToObject) {
      final VariableElement firstParam = method.getParameters().get(0);
      rawDeserializedType = firstParam.asType();
      deserializedType = environment.getAnyTypeElement(rawDeserializedType);
      rawSerializedType = method.getReturnType();
      serializedType = environment.getSupportedSerializedTypeElement(rawSerializedType);
    }

    hasObjectToDb = true;
  }

  public void addDbValueToObjectMethod(ExecutableElement method) {
    final TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();

    this.dbValueToObjectMethod = method;
    this.deserializedTypeTransformerElement = enclosingElement;
    this.deserializedTypeTransformerClassName = ClassName.get(enclosingElement);

    if (!hasObjectToDb) {
      final VariableElement firstParam = method.getParameters().get(0);
      rawSerializedType = firstParam.asType();
      serializedType = environment.getAnyTypeElement(rawSerializedType);
      rawDeserializedType = method.getReturnType();
      deserializedType = environment.getSupportedSerializedTypeElement(rawDeserializedType);
    }

    hasDbToObject = true;
  }

  public boolean isMissingMethods() {
    return !hasObjectToDb || !hasDbToObject;
  }

  @NonNull
  public FormatData serializedValueGetter(@NonNull String valueGetter) {
    return FormatData.create("$T.$L($L)", serializedTypeTransformerClassName, getSerializingMethodName(), valueGetter);
  }

  @NonNull
  public FormatData deserializedValueGetter(@NonNull String valueGetter) {
    return FormatData.create("$T.$L($L)", deserializedTypeTransformerClassName, getDeserializingMethodName(), valueGetter);
  }

  @NonNull
  public FormatData deserializedValueSetter(@NonNull String settableValue) {
    return FormatData.create("$T.$L(" + settableValue + ")",
        deserializedTypeTransformerClassName,
        getDeserializingMethodName());
  }

  @Nullable
  public String cursorParserConstantName(@NonNull Environment environment, boolean nullable) {
    return Const.cursorParserConstantName(getSerializedType(), environment, nullable);
  }

  public String getQualifiedSerializedName() {
    return serializedType.getQualifiedName();
  }

  public String getQualifiedDeserializedName() {
    return deserializedType.getQualifiedName();
  }

  public TypeName getDeserializedTypeName() {
    return Environment.getTypeName(deserializedType.getTypeElement());
  }

  public TypeName getDeserializedTypeNameForGenerics() {
    return typeNameForGenerics(getDeserializedType());
  }

  public TypeName getSerializedTypeName() {
    return Environment.getTypeName(serializedType.getTypeElement());
  }

  public TypeName getSerializedTypeNameForGenerics() {
    return typeNameForGenerics(getSerializedType());
  }

  public String getDeserializingMethodName() {
    return dbValueToObjectMethod.getSimpleName().toString();
  }

  public String getSerializingMethodName() {
    return objectToDbValueMethod.getSimpleName().toString();
  }

  public boolean isNumericType() {
    return Const.NUMERIC_SQL_TYPE_MAP.containsKey(getSerializedType().getQualifiedName());
  }

  public String getTransformerName() {
    final List<String> simpleNames = ClassName.get(deserializedType.getTypeElement()).simpleNames();
    return StringUtil.join("_", simpleNames);
  }

  public boolean cannotTransformNullValues() {
    return cannotTransformNullValues;
  }

  public void markAsCannotTransformNullValues() {
    cannotTransformNullValues = true;
  }

  public Element getRandomBlameElement() {
    if (objectToDbValueMethod != null) {
      return objectToDbValueMethod;
    }
    return dbValueToObjectMethod;
  }
}
