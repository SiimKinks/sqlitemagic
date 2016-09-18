package com.siimkinks.sqlitemagic.element;

import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.StringUtil;

import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Builder;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"columnName"}, callSuper = false)
public final class MethodColumnElement extends ColumnElement {

	private final Environment environment;
	private final ExecutableElement columnElement;
	private final Set<Modifier> modifiers;
	private final Column columnAnnotation;
	private final Id idAnnotation;
	private final TableElement enclosingTable;
	private final String sqlType;
	// real Java type e.g. Author
	private final ExtendedTypeElement deserializedType;
	private final TransformerElement transformer;
	private final TableElement referencedTable;

	private final String elementName;
	private final String columnName;
	private final String getterString;

	private final boolean nullable;
	private final boolean hasNullableAnnotation;

	public static MethodColumnElement create(Environment environment,
	                                         ExecutableElement columnElement,
	                                         Column columnAnnotation,
	                                         TableElement enclosingTable) {
		final TypeMirror returnType = columnElement.getReturnType();
		final ExtendedTypeElement deserializedType = environment.getAnyTypeElement(returnType);
		final TransformerElement transformer = environment.getTransformerFor(deserializedType);
		final TableElement referencedTable = environment.getTableElementFor(deserializedType.getQualifiedName());
		final ExtendedTypeElement serializedType = getSerializedType(environment, deserializedType, transformer, referencedTable);
		final String sqlType = getSqlType(deserializedType, serializedType);
		final String methodName = columnElement.getSimpleName().toString();
		final boolean nullableAnnotation = WriterUtil.hasNullableAnnotation(columnElement);

		return MethodColumnElement.builder()
				.environment(environment)
				.columnElement(columnElement)
				.modifiers(columnElement.getModifiers())
				.columnAnnotation(columnAnnotation)
				.idAnnotation(columnElement.getAnnotation(Id.class))
				.enclosingTable(enclosingTable)
				.sqlType(sqlType)
				.deserializedType(deserializedType)
				.transformer(transformer)
				.referencedTable(referencedTable)
				.elementName(methodName)
				.columnName(getColumnName(columnElement, columnAnnotation.value()))
				.getterString(methodName + "()")
				.nullable(deserializedType.isPrimitiveElement() ? false : nullableAnnotation)
				.hasNullableAnnotation(nullableAnnotation)
				.build();
	}

	static String getColumnName(ExecutableElement columnElement, String userDefinedColumnName) {
		String columnName = userDefinedColumnName;
		if (Strings.isNullOrEmpty(columnName)) {
			columnName = WriterUtil.nameWithoutJavaBeansPrefix(columnElement);
			return StringUtil.replaceCamelCaseWithUnderscore(columnName);
		}
		return columnName;
	}

	@Override
	public String valueSetter(String entityElementVariableName, String settableValue) {
		return enclosingTable.getValueWriter().buildOneValueSetterFromProvidedVariable(entityElementVariableName, settableValue, this);
	}

	@Override
	public FormatData deserializedValueSetter(@Nullable String entityElementVariableName, String settableValue, String managerVariableName) {
		if (hasTransformer()) {
			return transformer.deserializedValueSetter(settableValue);
		} else if (getDeserializedType().isBoxedByteArray(getEnvironment())) {
			return FormatData.create("$T.toByteArray(" + settableValue + ")", Utils.class);
		}
		return FormatData.create(settableValue);
	}

	@Override
	public boolean hasNullableAnnotation() {
		return hasNullableAnnotation;
	}
}
