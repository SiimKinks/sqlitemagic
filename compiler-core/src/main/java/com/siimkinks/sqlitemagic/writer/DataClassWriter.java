package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.squareup.javapoet.CodeBlock;

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.getDefaultValue;
import static com.siimkinks.sqlitemagic.WriterUtil.typeName;
import static com.siimkinks.sqlitemagic.element.FieldColumnElement.getterStringForField;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DataClassWriter implements ValueWriter {
  private final Environment environment;
  private final List<? extends BaseColumnElement> allColumns;
  private final ImmutableSet<VariableElement> allFields;
  @Nullable
  private final TableElement tableElement;
  private final TypeElement enclosingElement;
  private final String simpleClassName;

  public static DataClassWriter create(Environment environment,
                                       List<? extends BaseColumnElement> allColumns,
                                       ImmutableSet<VariableElement> allFields,
                                       TypeElement enclosingElement,
                                       @Nullable TableElement tableElement,
                                       String simpleClassName) {
    return DataClassWriter.builder()
        .environment(environment)
        .allColumns(allColumns)
        .allFields(allFields)
        .tableElement(tableElement)
        .enclosingElement(enclosingElement)
        .simpleClassName(simpleClassName)
        .build();
  }

  @Override
  public String buildOneValueSetter(String settableVariableName, BaseColumnElement settableColumn) {
    return String.format("new %s(%s)",
        simpleClassName,
        constructorArgsWithOneValue(settableVariableName, settableColumn));
  }

  @Override
  public CodeBlock buildAllValuesReturningSetter(Callback settableValueCallback) {
    final CodeBlock.Builder preCodeBuilder = CodeBlock.builder();
    final CodeBlock.Builder builder = CodeBlock.builder()
        .add("return new $L(", simpleClassName);
    final int columnsSize = allColumns.size();
    int lastColumnPos = 0;
    boolean first = true;
    for (VariableElement field : allFields) {
      if (first) {
        first = false;
      } else {
        builder.add(", ");
      }
      builder.add("\n\t\t");
      final String fieldName = field.getSimpleName().toString();
      final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
      if (columnElement != null && fieldName.equals(columnElement.getElementName())) {
        settableValueCallback.call(builder, preCodeBuilder, columnElement, lastColumnPos, columnElement.deserializedValueSetter(null, "%s", MANAGER_VARIABLE));
        lastColumnPos++;
      } else {
        final String defaultValue = getDefaultValue(typeName(field.asType()));
        builder.add(defaultValue);
      }
    }
    builder.add(")")
        .add(codeBlockEnd());
    preCodeBuilder.add(builder.build());
    return preCodeBuilder.build();
  }

  @Override
  public String buildOneValueSetterFromProvidedVariable(String entityVariableName, String settableValueName, BaseColumnElement settableColumn) {
    return String.format("new %s(%s)",
        simpleClassName,
        copyConstructorArgsWithSettingOneNewValue(entityVariableName, settableValueName, settableColumn));
  }

  private String copyConstructorArgsWithSettingOneNewValue(String entityVariableName,
                                                           String settableVariableName,
                                                           BaseColumnElement settableColumn) {
    final StringBuilder sb = new StringBuilder();
    final int columnsSize = allColumns.size();
    int lastColumnPos = 0;
    boolean first = true;
    for (VariableElement field : allFields) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      final String fieldName = field.getSimpleName().toString();
      final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
      if (columnElement != null && fieldName.equals(columnElement.getElementName())) {
        lastColumnPos++;
        if (columnElement.equals(settableColumn)) {
          sb.append(settableVariableName);
          continue;
        }
      }
      sb.append(entityVariableName)
          .append('.')
          .append(columnElement != null ? columnElement.getGetterString() : getterStringForField(environment, field, enclosingElement, tableElement));
    }
    return sb.toString();
  }

  private String constructorArgsWithOneValue(String settableVariableName, BaseColumnElement settableColumn) {
    final StringBuilder sb = new StringBuilder();
    final int columnsSize = allColumns.size();
    int lastColumnPos = 0;
    boolean first = true;
    for (VariableElement field : allFields) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      final String fieldName = field.getSimpleName().toString();
      final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
      if (columnElement != null && fieldName.equals(columnElement.getElementName())) {
        lastColumnPos++;
        if (columnElement.equals(settableColumn)) {
          sb.append(settableVariableName);
          continue;
        }
      }
      final String defaultValue = getDefaultValue(typeName(field.asType()));
      sb.append(defaultValue);
    }
    return sb.toString();
  }
}
