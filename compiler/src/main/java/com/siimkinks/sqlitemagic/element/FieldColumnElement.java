package com.siimkinks.sqlitemagic.element;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.StringUtil;

import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"columnName"}, callSuper = false)
public class FieldColumnElement extends ColumnElement {

	private final Environment environment;
	private final VariableElement columnElement;
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
	private final String setterString;

	private final boolean nullable;
	private final boolean hasNullableAnnotation;

	public static FieldColumnElement create(Environment environment, VariableElement columnElement, Column columnAnnotation, TableElement enclosingTable) {
		final ExtendedTypeElement deserializedType = environment.getAnyTypeElement(columnElement);
		final TransformerElement transformer = environment.getTransformerFor(deserializedType);
		final TableElement referencedTable = environment.getTableElementFor(deserializedType.getQualifiedName());
		final ExtendedTypeElement serializedType = getSerializedType(environment, deserializedType, transformer, referencedTable);
		final String sqlType = getSqlType(deserializedType, serializedType);
		final String fieldName = columnElement.getSimpleName().toString();

		return FieldColumnElement.builder()
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
				.elementName(fieldName)
				.columnName(getColumnName(columnElement, columnAnnotation))
				.getterString(getGetterString(columnAnnotation, enclosingTable, fieldName, deserializedType))
				.setterString(getSetterString(columnAnnotation, enclosingTable, fieldName))
				.nullable(determineNullability(deserializedType, columnElement))
				.hasNullableAnnotation(WriterUtil.hasNullableAnnotation(columnElement))
				.build();
	}

	private static boolean determineNullability(ExtendedTypeElement deserializedType, VariableElement columnElement) {
		return (!deserializedType.isPrimitiveElement() || deserializedType.isArrayElement()) && !WriterUtil.hasNotNullAnnotation(columnElement);
	}

	private static String getSetterString(Column columnAnnotation, TableElement enclosingTable, String fieldName) {
		if (useAccessMethods(columnAnnotation, enclosingTable)) {
			return enclosingTable.getMethodNameForSettingField(fieldName);
		}
		return fieldName;
	}

	private static String getGetterString(Column columnAnnotation, TableElement enclosingTable, String fieldName, ExtendedTypeElement deserializedType) {
		if (useAccessMethods(columnAnnotation, enclosingTable)) {
			final boolean isPrimitiveBoolean = deserializedType.getTypeElement().asType() == Const.BOOLEAN_TYPE && deserializedType.isPrimitiveElement();
			final String methodNameForGettingField = enclosingTable.getMethodNameForGettingField(fieldName, isPrimitiveBoolean);
			if (methodNameForGettingField == null) {
				return null;
			}
			return methodNameForGettingField + "()";
		}
		return fieldName;
	}

	private static String getColumnName(VariableElement columnElement, Column columnAnnotation) {
		String columnName = columnAnnotation.value();
		if (Strings.isNullOrEmpty(columnName)) {
			return transformColumnName(columnElement);
		}
		return columnName;
	}

	private static String transformColumnName(Element element) {
		String rawName = element.getSimpleName().toString();
		return StringUtil.replaceCamelCaseWithUnderscore(rawName);
	}

	private boolean hasSetterMethod() {
		return useAccessMethods(columnAnnotation, enclosingTable);
	}

	@Override
	public boolean hasNullableAnnotation() {
		return hasNullableAnnotation;
	}

	public static boolean useAccessMethods(Column columnAnnotation, TableElement enclosingTable) {
		return columnAnnotation.useAccessMethods() || enclosingTable.useAccessMethods();
	}

	@Override
	public String valueSetter(String entityElementVariableName, String settableValue) {
		if (!hasSetterMethod()) {
			return String.format("%s.%s = %s", entityElementVariableName, setterString, settableValue);
		}
		return String.format("%s.%s(%s)", entityElementVariableName, setterString, settableValue);
	}

	@Override
	public FormatData deserializedValueSetter(String entityElementVariableName, String settableValue, String managerVariableName) {
		if (hasTransformer()) {
			final FormatData transformedValue = transformer.deserializedValueSetter(settableValue);
			return FormatData.create(valueSetter(entityElementVariableName, transformedValue.getFormat()),
					transformedValue.getArgs());
		} else if (getDeserializedType().isBoxedByteArray(getEnvironment())) {
			String valueSetter = valueSetter(entityElementVariableName, "$T.toByteArray($L)");
			return FormatData.create(valueSetter, UTIL, settableValue);
		}
		return FormatData.create(valueSetter(entityElementVariableName, settableValue));
	}

}
