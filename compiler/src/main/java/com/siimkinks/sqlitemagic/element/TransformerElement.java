package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Const;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.validator.TransformerValidator;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import lombok.Getter;
import lombok.ToString;

import static com.siimkinks.sqlitemagic.WriterUtil.typeNameForGenerics;

/**
 * @author Siim Kinks
 */
@ToString
public class TransformerElement {

	@Getter
	private final TypeElement element;
	@Getter
	private final ClassName transformerClassName;
	@Getter
	private ExtendedTypeElement deserializedType;
	@Getter
	private TypeMirror rawDeserializedType;
	@Getter
	private ExtendedTypeElement serializedType;
	@Getter
	private TypeMirror rawSerializedType;
	@Getter
	private ExecutableElement objectToDbValueStaticMethod;
	@Getter
	private ExecutableElement dbValueToObjectStaticMethod;
	private boolean cannotTransformNullValues = false;

	@Getter
	private boolean missingMethods = false;

	public TransformerElement(Environment environment, Element element) {
		this.element = (TypeElement) element;
		transformerClassName = ClassName.get(this.element);
		handleStaticTransformer(environment);
	}

	private void handleStaticTransformer(Environment environment) {
		boolean hasObjectToDb = false;
		boolean hasDbToObject = false;
		ExecutableElement firstTransformMethod = null;
		for (Element enclosedElement : element.getEnclosedElements()) {
			if (enclosedElement.getKind() == ElementKind.METHOD) {
				ExecutableElement method = (ExecutableElement) enclosedElement;
				Annotation annotation = method.getAnnotation(ObjectToDbValue.class);
				if (annotation != null) {
					hasObjectToDb = true;
					if (!TransformerValidator.isTransformerStaticMethodValid(environment, method, firstTransformMethod)) {
						break;
					}
					objectToDbValueStaticMethod = method;
					VariableElement firstParam = method.getParameters().get(0);
					rawDeserializedType = firstParam.asType();
					deserializedType = environment.getAnyTypeElement(rawDeserializedType);
					rawSerializedType = method.getReturnType();
					serializedType = environment.getSupportedSerializedTypeElement(rawSerializedType);
					if (firstTransformMethod == null) {
						firstTransformMethod = method;
					} else {
						break; // we found all methods
					}
				} else {
					annotation = method.getAnnotation(DbValueToObject.class);
					if (annotation != null) {
						hasDbToObject = true;
						if (!TransformerValidator.isTransformerStaticMethodValid(environment, method, firstTransformMethod)) {
							break;
						}
						dbValueToObjectStaticMethod = method;
						if (firstTransformMethod == null) {
							firstTransformMethod = method;
						} else {
							break; // we found all methods
						}
					}
				}
			}
		}
		missingMethods = !hasObjectToDb || !hasDbToObject;
	}

	@NonNull
	public FormatData serializedValueGetter(@NonNull String valueGetter) {
		return FormatData.create("$T.$L($L)", transformerClassName, getSerializingMethodName(), valueGetter);
	}

	@NonNull
	public FormatData deserializedValueGetter(@NonNull String valueGetter) {
		return FormatData.create("$T.$L($L)", transformerClassName, getDeserializingMethodName(), valueGetter);
	}

	@NonNull
	public FormatData deserializedValueSetter(@NonNull String settableValue) {
		return FormatData.create("$T.$L(" + settableValue + ")",
				transformerClassName,
				getDeserializingMethodName());
	}

	@Nullable
	public String cursorParserConstantName(@NonNull Environment environment) {
		return Const.cursorParserConstantName(getSerializedType(), environment);
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
		return dbValueToObjectStaticMethod.getSimpleName().toString();
	}

	public String getSerializingMethodName() {
		return objectToDbValueStaticMethod.getSimpleName().toString();
	}

	public boolean isNumericType() {
		return Const.NUMERIC_SQL_TYPE_MAP.containsKey(getSerializedType().getQualifiedName());
	}

	public String getClassName() {
		return element.getSimpleName().toString();
	}

	public boolean cannotTransformNullValues() {
		return cannotTransformNullValues;
	}

	public void markAsCannotTransformNullValues() {
		cannotTransformNullValues = true;
	}
}
