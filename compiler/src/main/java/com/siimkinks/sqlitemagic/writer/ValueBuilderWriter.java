package com.siimkinks.sqlitemagic.writer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.MethodColumnElement;
import com.siimkinks.sqlitemagic.util.Dual;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.getDefaultValue;
import static com.siimkinks.sqlitemagic.WriterUtil.nameWithoutJavaBeansPrefix;
import static com.siimkinks.sqlitemagic.WriterUtil.typeName;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ValueBuilderWriter implements ValueWriter {

  private final Environment environment;
  private final TypeElement builderClassElement;
  private final String abstractClassName;
  private final ExecutableElement buildMethod;
  private final Dual<BaseColumnElement, ExecutableElement>[] requiredElements;
  private final ImmutableList<ExecutableElement> ignoredElements;

  public static ValueBuilderWriter create(Environment environment,
                                          TypeElement builderClassElement,
                                          TypeMirror returnType,
                                          List<? extends BaseColumnElement> allColumns,
                                          String abstractClassName) {
    final Types typeUtil = environment.getTypeUtils();
    ExecutableElement buildMethod = null;
    final Map<String, Dual<BaseColumnElement, Integer>> columns = new HashMap<>();

    for (int i = 0, allColumnsSize = allColumns.size(); i < allColumnsSize; i++) {
      BaseColumnElement columnElement = allColumns.get(i);
      final String methodName = nameWithoutJavaBeansPrefix(columnElement.getElementName()).toLowerCase();
      columns.put(methodName, Dual.create(columnElement, i));
    }

    final Dual<BaseColumnElement, ExecutableElement>[] requiredElements = new Dual[allColumns.size()];
    final List<ExecutableElement> ignoredElements = Lists.newArrayList();

    int collectedVals = 0;
    for (ExecutableElement methodElement : environment.getLocalAndInheritedMethods(builderClassElement)) {
      final Set<Modifier> modifiers = methodElement.getModifiers();
      if (!modifiers.contains(Modifier.ABSTRACT)) continue;

      if (typeUtil.isSameType(returnType, methodElement.getReturnType())
          && methodElement.getParameters().isEmpty()) {
        buildMethod = methodElement;
        continue;
      }
      final String methodName = nameWithoutJavaBeansPrefix(methodElement).toLowerCase();
      final Dual<BaseColumnElement, Integer> columnData = columns.get(methodName);
      if (columnData == null) {
        // Builder element has no matching column element
        // -- probably @IgnoreColumn annotated method
        ignoredElements.add(methodElement);
        continue;
      }
      final BaseColumnElement columnElement = columnData.getFirst();
      requiredElements[columnData.getSecond()] = Dual.create(columnElement, methodElement);
      collectedVals++;
    }
    if (buildMethod == null) {
      throw new RuntimeException("Builder class has no correct build method");
    }
    if (collectedVals != allColumns.size()) {
      throw new RuntimeException("Table class \"" + abstractClassName + "\" builder class \""
          + builderClassElement.getSimpleName().toString() + "\" is missing " +
          Math.abs(collectedVals - allColumns.size()) + " methods");
    }

    return ValueBuilderWriter.builder()
        .environment(environment)
        .builderClassElement(builderClassElement)
        .abstractClassName(abstractClassName)
        .buildMethod(buildMethod)
        .requiredElements(requiredElements)
        .ignoredElements(ImmutableList.copyOf(ignoredElements))
        .build();
  }

  @Override
  public String buildOneValueSetter(String settableVariableName, BaseColumnElement settableColumn) {
    return buildNew(oneValueBuildMethod(settableVariableName, settableColumn));
  }

  @Override
  public CodeBlock buildAllValuesReturningSetter(Callback settableValueCallback) {
    final CodeBlock.Builder preCodeBuilder = CodeBlock.builder();
    final String builderClass = String.format("%s.%s",
        abstractClassName,
        builderClassElement.getSimpleName().toString());
    final CodeBlock.Builder builder = CodeBlock.builder()
        .addStatement("final $L builder = new $L()",
            builderClass,
            environment.getValueImplementationClassNameString(builderClass));
    for (int i = 0, length = requiredElements.length; i < length; i++) {
      final Dual<BaseColumnElement, ExecutableElement> element = requiredElements[i];
      final BaseColumnElement columnElement = element.getFirst();
      final FormatData columnSetterFormat = columnElement.deserializedValueSetter(null, "%s", MANAGER_VARIABLE);

      final FormatData valueSetterFormat = FormatData.create(String.format(
          "builder." + element.getSecond().getSimpleName().toString() + "(%s);\n", columnSetterFormat.getFormat()),
          columnSetterFormat.getArgs());
      settableValueCallback.call(builder, preCodeBuilder, columnElement, i, valueSetterFormat);
    }
    for (int i = 0, ignoredElementsSize = ignoredElements.size(); i < ignoredElementsSize; i++) {
      final ExecutableElement ignoredElement = ignoredElements.get(i);
      final VariableElement firstParam = ignoredElement.getParameters().get(0);
      final TypeName paramTypeName = typeName(firstParam.asType());
      if (paramTypeName.isPrimitive()) {
        final String defaultValue = getDefaultValue(paramTypeName);
        builder.addStatement("builder.$L($L)",
            ignoredElement.getSimpleName().toString(),
            defaultValue);
      }
    }
    builder.addStatement("return builder.$L()", buildMethod.getSimpleName().toString());
    preCodeBuilder.add(builder.build());
    return preCodeBuilder.build();
  }

  @Override
  public String buildOneValueSetterFromProvidedVariable(String entityVariableName, String settableValueName, MethodColumnElement settableColumn) {
    return buildNewFromPrevious(entityVariableName, oneValueBuildMethod(settableValueName, settableColumn));
  }

  private String buildNewFromPrevious(String prevBuilder, String builderMethods) {
    return String.format("new %s.%s(%s)%s.%s()",
        environment.getValueImplementationClassNameString(abstractClassName),
        builderClassElement.getSimpleName().toString(),
        prevBuilder,
        builderMethods,
        buildMethod.getSimpleName().toString());
  }

  private String buildNew(String builderMethods) {
    return String.format("new %s.%s()%s.%s()",
        environment.getValueImplementationClassNameString(abstractClassName),
        builderClassElement.getSimpleName().toString(),
        builderMethods,
        buildMethod.getSimpleName().toString());
  }

  private String oneValueBuildMethod(String settableVariableName, BaseColumnElement settableColumn) {
    for (Dual<BaseColumnElement, ExecutableElement> element : requiredElements) {
      if (element.getFirst().equals(settableColumn)) {
        return "." + element.getSecond().getSimpleName().toString() + "(" + settableVariableName + ")";
      }
    }
    // TODO rename error msg
    throw new IllegalStateException("Missing column " + settableColumn.getColumnName());
  }
}
