package com.siimkinks.sqlitemagic.validator;

import com.google.common.base.Joiner;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.siimkinks.sqlitemagic.Const.ERR_MULTIPLE_TRANSFORMERS;
import static com.siimkinks.sqlitemagic.Const.ERR_TRANSFORM_TYPES_NOT_MATCHING;
import static com.siimkinks.sqlitemagic.Const.SQL_TYPE_MAP;
import static com.siimkinks.sqlitemagic.Const.SUPPORTED_SQL_BOXED_TYPES;

@Singleton
public class TransformerValidator {

  final Environment environment;

  @Inject
  public TransformerValidator(Environment environment) {
    this.environment = environment;
  }

  public boolean isTransformerValid(TransformerElement transformerElement) {
    final Element blameElement = transformerElement.getRandomBlameElement();
    if (transformerElement.isMissingMethods()) {
      environment.error(blameElement, "There must be 2 static annotated transform methods -- " +
              "@%s annotated method which transforms Java objects to SQLite compatible objects and " +
              "@%s annotated method which transforms database values to Java objects.\n" +
              "%s",
          ObjectToDbValue.class.getSimpleName(),
          DbValueToObject.class.getSimpleName(),
          transformerElement.missingMethodsErrorInfo());
      return false;
    }
    if (!isTransformerMethodValid(environment, transformerElement.getObjectToDbValueMethod(), transformerElement.getDbValueToObjectMethod())) {
      return false;
    }
    if (!isTransformerMethodValid(environment, transformerElement.getDbValueToObjectMethod(), transformerElement.getObjectToDbValueMethod())) {
      return false;
    }
    final TypeElement serializedType = transformerElement.getSerializedType().getTypeElement();
    if (serializedType == null) {
      environment.error(blameElement, "Unsupported serialization type %s", transformerElement.getRawSerializedType().toString());
      return false;
    }
    final ExtendedTypeElement deserializedType = transformerElement.getDeserializedType();
    if (deserializedType.getTypeElement() == null) {
      environment.error(blameElement, "Unsupported deserialization type %s", transformerElement.getRawDeserializedType().toString());
      return false;
    }
    final String deserializedQualifiedName = deserializedType.getQualifiedName();
    if (SQL_TYPE_MAP.get(deserializedQualifiedName) != null) {
      environment.error(blameElement, "SQL types [%s] can't have transformers",
          Joiner.on(", ").join(SUPPORTED_SQL_BOXED_TYPES));
      return false;
    }
    final String serializedTypeQualifiedName = Environment.getQualifiedName(serializedType);
    if (SQL_TYPE_MAP.get(serializedTypeQualifiedName) == null) {
      environment.error(blameElement,
          "Provided serialization type was %s, but serialized type must be one of supported SQLite types - %s",
          serializedTypeQualifiedName,
          Joiner.on(", ").join(SUPPORTED_SQL_BOXED_TYPES));
      return false;
    }
    if (environment.hasTransformerFor(deserializedType)) {
      environment.error(blameElement, ERR_MULTIPLE_TRANSFORMERS + deserializedQualifiedName);
      return false;
    }
    if (deserializedType.getTypeElement().getAnnotation(Table.class) != null) {
      environment.error(blameElement, "Cannot transform object %s which is also annotated with @%s. Delete transformer or remove annotation",
          deserializedQualifiedName, Table.class.getSimpleName());
      return false;
    }
    return true;
  }

  private static boolean isTransformerMethodValid(Environment environment,
                                                 ExecutableElement method,
                                                 ExecutableElement otherMethod) {
    if (method.getParameters().size() != 1) {
      environment.error(method, "Transformer methods must have one parameter");
      return false;
    }
    if (!method.getModifiers().contains(Modifier.STATIC)) {
      environment.error(method, "Transformer methods must be static");
      return false;
    }
    if (otherMethod != null) {
      if (!assertReturnTypeSameAsFirstParamType(environment, method, otherMethod)) {
        return false;
      }
      if (!assertReturnTypeSameAsFirstParamType(environment, otherMethod, method)) {
        return false;
      }
    }
    return true;
  }

  private static boolean assertReturnTypeSameAsFirstParamType(Environment environment, ExecutableElement firstMethod, ExecutableElement secondMethod) {
    TypeMirror firstParam = firstMethod.getParameters().get(0).asType();
    TypeMirror returnType = secondMethod.getReturnType();
    if (!environment.getTypeUtils().isSameType(firstParam, returnType)) {
      environment.error(firstMethod, ERR_TRANSFORM_TYPES_NOT_MATCHING);
      return false;
    }
    return true;
  }
}
