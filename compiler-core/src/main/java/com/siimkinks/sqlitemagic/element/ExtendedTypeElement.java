package com.siimkinks.sqlitemagic.element;

import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.util.Dual;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import androidx.annotation.NonNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import static com.siimkinks.sqlitemagic.Const.STRING_TYPE;

@Data
@ToString(doNotUseGetters = true)
@AllArgsConstructor
public class ExtendedTypeElement {
  private final Dual<TypeElement, Boolean> typeElement;
  private final TypeMirror typeMirror;
  private final boolean isArrayElement;
  private final boolean isGenericElement;

  public static final ExtendedTypeElement EMPTY = new ExtendedTypeElement(null, null, false, false);
  private static ExtendedTypeElement LONG;
  private static ExtendedTypeElement PRIMITIVE_LONG;

  public static ExtendedTypeElement LONG(Environment environment) {
    if (LONG == null) {
      LONG = new ExtendedTypeElement(Dual.create(environment.getTypeElement(Long.class), false));
    }
    return LONG;
  }

  public static ExtendedTypeElement PRIMITIVE_LONG(Environment environment) {
    if (PRIMITIVE_LONG == null) {
      PRIMITIVE_LONG = new ExtendedTypeElement(Dual.create(environment.getTypeElement(Long.class), true));
    }
    return PRIMITIVE_LONG;
  }

  public ExtendedTypeElement(Dual<TypeElement, Boolean> element) {
    this.typeElement = element;
    this.typeMirror = getTypeMirror(element);
    this.isArrayElement = false;
    this.isGenericElement = false;
  }

  public String getTypeKey() {
    if (isGenericElement) {
      return typeMirror.toString();
    }
    return getQualifiedName();
  }

  public String getQualifiedName() {
    String qualifiedName = Environment.getQualifiedName(getTypeElement());
    if (isArrayElement) {
      qualifiedName += "[]";
    }
    return qualifiedName;
  }

  public TypeElement getTypeElement() {
    return typeElement != null ? typeElement.getFirst() : null;
  }

  public boolean isPrimitiveElement() {
    return typeElement != null ? typeElement.getSecond() : false;
  }

  public TypeName asTypeName() {
    final ClassName className = ClassName.get(getTypeElement());
    if (isArrayElement) {
      return ArrayTypeName.of(className);
    }
    return className;
  }

  public boolean isStringType(@NonNull Environment environment) {
    final Types typeUtils = environment.getTypeUtils();
    return typeUtils.isSubtype(typeMirror, STRING_TYPE);
  }

  private static TypeMirror getTypeMirror(Dual<TypeElement, Boolean> element) {
    if (element != null) {
      final TypeElement typeElement = element.getFirst();
      if (typeElement != null) {
        return typeElement.asType();
      }
    }
    return null;
  }

  public boolean isBoxedByteArray(Environment environment) {
    return environment.getTypeUtils().isSameType(getTypeElement().asType(), Const.BYTE_TYPE)
        && !isPrimitiveElement() && isArrayElement();
  }

  public boolean isPrimitiveByteArray(Environment environment) {
    return environment.getTypeUtils().isSameType(getTypeElement().asType(), Const.BYTE_TYPE)
        && isPrimitiveElement() && isArrayElement();
  }

  public List<TypeElement> getAllGenericTypeElements(Elements elementUtils) {
    if (!isGenericElement) {
      return Collections.emptyList();
    }
    final String type = this.typeMirror.toString();
    final String[] rawTypes = type
        .replace(">", "")
        .split("[<,]");
    final ArrayList<TypeElement> output = new ArrayList<>();
    for (int i = 0, length = rawTypes.length; i < length; i++) {
      final TypeElement typeElement = elementUtils.getTypeElement(rawTypes[i]);
      output.add(typeElement);
    }
    return output;
  }
}
