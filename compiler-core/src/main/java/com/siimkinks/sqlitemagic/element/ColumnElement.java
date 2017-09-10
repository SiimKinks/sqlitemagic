package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.TypeName;

import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.siimkinks.sqlitemagic.Const.CHAR_SEQUENCE_TYPE;
import static com.siimkinks.sqlitemagic.Const.CURSOR_GETTER_CAST_MAP;
import static com.siimkinks.sqlitemagic.Const.CURSOR_METHOD_MAP;
import static com.siimkinks.sqlitemagic.Const.NUMBER_TYPE;
import static com.siimkinks.sqlitemagic.WriterUtil.CHAR_SEQUENCE;
import static com.siimkinks.sqlitemagic.WriterUtil.NUMBER;
import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.typeNameForGenerics;

/**
 * @author Siim Kinks
 */
public abstract class ColumnElement implements BaseColumnElement {
  public abstract String getColumnName();

  public abstract TableElement getReferencedTable();

  abstract String getSqlType();

  abstract Id getIdAnnotation();

  abstract Unique getUniqueAnnotation();

  public abstract TransformerElement getTransformer();

  public abstract Column getColumnAnnotation();

  abstract Set<Modifier> getModifiers();

  public abstract String getGetterString();

  abstract Environment getEnvironment();

  abstract TableElement getEnclosingTable();

  public abstract String getElementName();

  /**
   * @return True if object is not primitive and can have {@code null} value.
   * False if the object is primitive or if there is {@code @Nullable} annotation on the column.
   */
  public abstract boolean isNullable();

  /**
   * @return True if user has not annotated column with {@code @Nullable} annotation.
   * False if there is {@code @Nullable} annotation on the column.
   */
  public abstract boolean hasNullableAnnotation();

  public abstract FormatData deserializedValueSetter(String entityElementVariableName, String settableValue, String managerVariableName);

  public abstract String valueSetter(String entityElementVariableName, String settableValue);

  public FormatData serializedValueGetterFromEntity(String entityElementVariableName) {
    String valueGetter = valueGetter(entityElementVariableName);
    return serializedValueGetter(valueGetter);
  }

  @NonNull
  public FormatData serializedValueGetter(String valueGetter) {
    if (hasTransformer()) {
      return getTransformer().serializedValueGetter(valueGetter);
    } else if (isReferencedColumn()) {
      return getReferencedTable().serializedValueGetter(valueGetter);
    } else if (getDeserializedType().isBoxedByteArray(getEnvironment())) {
      return FormatData.create("$T.toByteArray($L)", UTIL, valueGetter);
    }
    return FormatData.create("$L", valueGetter);
  }

  public ExtendedTypeElement getSerializedType() {
    return getSerializedType(getEnvironment(), getDeserializedType(), getTransformer(), getReferencedTable());
  }

  public TypeName getDeserializedTypeName() {
    final ExtendedTypeElement deserializedType = getDeserializedType();
    final TypeName typeName = TypeName.get(deserializedType.getTypeMirror());
    return deserializedType.isPrimitiveElement() ? typeName.unbox() : typeName;
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

  public static TypeName findEquivalentType(ExtendedTypeElement serializedType,
                                            Environment environment) {
    final Types typeUtils = environment.getTypeUtils();
    TypeMirror typeMirror = serializedType.getTypeMirror();
    if (typeMirror instanceof PrimitiveType) {
      typeMirror = typeUtils.boxedClass((PrimitiveType) typeMirror).asType();
    }
    if (typeUtils.isSubtype(typeMirror, NUMBER_TYPE)) {
      return NUMBER;
    }
    if (typeUtils.isSubtype(typeMirror, CHAR_SEQUENCE_TYPE)) {
      return CHAR_SEQUENCE;
    }
    return typeNameForGenerics(serializedType);
  }

  protected String getTransformerName() {
    return getTransformer().getClassName();
  }

  public boolean hasModifier(Modifier modifier) {
    return getModifiers().contains(modifier);
  }

  public String valueGetter(String entityElementVariableName) {
    if (!Strings.isNullOrEmpty(entityElementVariableName)) {
      return String.format("%s.%s", entityElementVariableName, getGetterString());
    }
    return getGetterString();
  }

  public String cursorGetter(String cursorVariable, String offsetString) {
    return cursorGetter(this, cursorVariable, offsetString);
  }

  public static String cursorGetter(ColumnElement columnElement, String cursorVariable, String offsetString) {
    final ExtendedTypeElement serializedType = columnElement.getSerializedType();
    return cursorGetter(serializedType, cursorVariable, offsetString);
  }

  public static String cursorGetter(ExtendedTypeElement serializedType, String cursorVariable, String offsetString) {
    final String qualifiedName = serializedType.getQualifiedName();
    final String cursorMethod = CURSOR_METHOD_MAP.get(qualifiedName);
    String cast = CURSOR_GETTER_CAST_MAP.get(qualifiedName);
    if (Strings.isNullOrEmpty(cast)) {
      cast = "";
    } else {
      cast += " ";
    }
    return String.format("%s%s.%s(%s)", cast, cursorVariable, cursorMethod, offsetString);
  }

  @Nullable
  public String cursorParserConstantName(@NonNull Environment environment) {
    return Const.cursorParserConstantName(getSerializedType(), environment, isNullable());
  }

  public boolean hasReferencedTable() {
    return getReferencedTable() != null;
  }

  public boolean isReferencedTableImmutable() {
    return hasReferencedTable() && getReferencedTable().isImmutable();
  }

  public boolean isHandledRecursively() {
    return getColumnAnnotation().handleRecursively() && isReferencedColumn();
  }

  public boolean isReferencedColumn() {
    return !hasTransformer() && !isSqlCompatibleType(getDeserializedType());
  }

  public boolean isId() {
    return getIdAnnotation() != null;
  }

  public boolean isAutoincrementId() {
    return isId() && getIdAnnotation().autoIncrement();
  }

  public boolean isUnique() {
    return getUniqueAnnotation() != null;
  }

  public boolean isOnDeleteCascade() {
    return isHandledRecursively() && getColumnAnnotation().onDeleteCascade();
  }

  public String getSchema() {
    StringBuilder schema = new StringBuilder(getColumnName());
    schema.append(" ")
        .append(getSqlType());

    if (isId()) {
      schema.append(" PRIMARY KEY");
      if (getIdAnnotation().autoIncrement()) {
        schema.append(" AUTOINCREMENT");
      }
    } else if (isUnique()) {
      schema.append(" UNIQUE");
    }
    if (isOnDeleteCascade()) {
      final TableElement referencedTable = getReferencedTable();
      schema.append(" REFERENCES ")
          .append(referencedTable.getTableName())
          .append("(")
          .append(referencedTable.getIdColumn().getColumnName())
          .append(") ON DELETE CASCADE");
    }
    return schema.toString();
  }

  @Override
  public boolean hasTransformer() {
    return getTransformer() != null;
  }

  public boolean isNeededForShallowQuery() {
    return isReferencedTableImmutable()
        && isHandledRecursively()
        && getReferencedTable().hasAnyNonIdNotNullableColumns();
  }

  @Override
  public boolean isNumericType() {
    return Const.NUMERIC_SQL_TYPE_MAP.containsKey(getSerializedType().getQualifiedName());
  }

  protected static boolean isSqlCompatibleType(ExtendedTypeElement type) {
    return Const.SQL_TYPE_MAP.containsKey(type.getQualifiedName());
  }

  public static String getSqlTypeFromTypeElement(ExtendedTypeElement deserializedType) {
    return Const.SQL_TYPE_MAP.get(deserializedType.getQualifiedName());
  }

  protected static String getSqlTypeFromTypeElement(TypeElement deserializedType) {
    return Const.SQL_TYPE_MAP.get(Environment.getQualifiedName(deserializedType));
  }

  /**
   * Primitive sqlite type e.g. boolean
   */
  protected static ExtendedTypeElement getSerializedType(Environment environment, ExtendedTypeElement deserializedType, TransformerElement transformer, TableElement referencedTable) {
    if (!isSqlCompatibleType(deserializedType)) {
      if (transformer != null) {
        return transformer.getSerializedType();
      } else if (referencedTable != null) {
        if (referencedTable.isIdCollected()) {
          return referencedTable.getIdColumn().getSerializedType();
        }
        return ExtendedTypeElement.LONG(environment);
      }
    }
    return deserializedType;
  }

  protected static String getSqlType(ExtendedTypeElement deserializedType, ExtendedTypeElement serializedType) {
    String sqlType = getSqlTypeFromTypeElement(deserializedType);
    if (sqlType == null) {
      sqlType = getSqlTypeFromTypeElement(serializedType);
    }
    return sqlType;
  }
}
