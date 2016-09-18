package com.siimkinks.sqlitemagic.writer;

import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.MethodColumnElement;
import com.squareup.javapoet.CodeBlock;

import java.util.List;

import javax.lang.model.element.ExecutableElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.WriterUtil.getDefaultValue;
import static com.siimkinks.sqlitemagic.WriterUtil.typeName;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ValueCreatorWriter implements ValueWriter {

	private final Environment environment;
	private final List<? extends BaseColumnElement> allColumns;
	private final String abstractClassName;
	private final ImmutableSet<ExecutableElement> allMethods;

	public static ValueCreatorWriter create(Environment environment,
	                                        List<? extends BaseColumnElement> allColumns,
	                                        ImmutableSet<ExecutableElement> allMethods,
	                                        String abstractClassName) {
		return ValueCreatorWriter.builder()
				.environment(environment)
				.allColumns(allColumns)
				.abstractClassName(abstractClassName)
				.allMethods(allMethods)
				.build();
	}

	@Override
	public String buildOneValueSetter(String settableVariableName, BaseColumnElement settableColumn) {
		return String.format("new %s(%s)",
				environment.getValueImplementationClassNameString(abstractClassName),
				constructorArgsWithOneValue(settableVariableName, settableColumn));
	}

	@Override
	public CodeBlock buildAllValuesReturningSetter(Callback settableValueCallback) {
		final CodeBlock.Builder preCodeBuilder = CodeBlock.builder();
		final CodeBlock.Builder builder = CodeBlock.builder()
				.add("return new $L(",
						environment.getValueImplementationClassNameString(abstractClassName));
		final int columnsSize = allColumns.size();
		int lastColumnPos = 0;
		boolean first = true;
		for (ExecutableElement method : allMethods) {
			if (first) {
				first = false;
			} else {
				builder.add(", ");
			}
			builder.add("\n\t\t");
			final String methodName = method.getSimpleName().toString();
			final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
			if (columnElement != null && methodName.equals(columnElement.getElementName())) {
				settableValueCallback.call(builder, preCodeBuilder, columnElement, lastColumnPos, columnElement.deserializedValueSetter(null, "%s", MANAGER_VARIABLE));
				lastColumnPos++;
			} else {
				final String defaultValue = getDefaultValue(typeName(method.getReturnType()));
				builder.add(defaultValue);
			}
		}
		builder.add(")")
				.add(codeBlockEnd());
		preCodeBuilder.add(builder.build());
		return preCodeBuilder.build();
	}

	@Override
	public String buildOneValueSetterFromProvidedVariable(String entityVariableName,
	                                                      String settableValueName,
	                                                      MethodColumnElement settableColumn) {
		return String.format("new %s(%s)",
				environment.getValueImplementationClassNameString(abstractClassName),
				copyConstructorArgsWithSettingOneNewValue(entityVariableName, settableValueName, settableColumn));
	}

	private String copyConstructorArgsWithSettingOneNewValue(String entityVariableName,
	                                                         String settableVariableName,
	                                                         BaseColumnElement settableColumn) {
		final StringBuilder sb = new StringBuilder();
		final int columnsSize = allColumns.size();
		int lastColumnPos = 0;
		boolean first = true;
		for (ExecutableElement method : allMethods) {
			if (first) {
				first = false;
			} else {
				sb.append(", ");
			}
			final String methodName = method.getSimpleName().toString();
			final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
			if (columnElement != null && methodName.equals(columnElement.getElementName())) {
				lastColumnPos++;
				if (columnElement.equals(settableColumn)) {
					sb.append(settableVariableName);
					continue;
				}
			}
			sb.append(entityVariableName)
					.append('.')
					.append(method.getSimpleName().toString())
					.append("()");
		}
		return sb.toString();
	}

	private String constructorArgsWithOneValue(String settableVariableName, BaseColumnElement settableColumn) {
		final StringBuilder sb = new StringBuilder();
		final int columnsSize = allColumns.size();
		int lastColumnPos = 0;
		boolean first = true;
		for (ExecutableElement method : allMethods) {
			if (first) {
				first = false;
			} else {
				sb.append(", ");
			}
			final String methodName = method.getSimpleName().toString();
			final BaseColumnElement columnElement = lastColumnPos < columnsSize ? allColumns.get(lastColumnPos) : null;
			if (columnElement != null && methodName.equals(columnElement.getElementName())) {
				lastColumnPos++;
				if (columnElement.equals(settableColumn)) {
					sb.append(settableVariableName);
					continue;
				}
			}
			final String defaultValue = getDefaultValue(typeName(method.getReturnType()));
			sb.append(defaultValue);
		}
		return sb.toString();
	}
}
