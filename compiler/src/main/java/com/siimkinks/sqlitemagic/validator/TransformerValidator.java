package com.siimkinks.sqlitemagic.validator;

import android.support.annotation.NonNull;

import com.google.common.base.Joiner;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.util.ReturnCallback;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS;
import static com.siimkinks.sqlitemagic.Const.ERR_MULTIPLE_TRANSFORMERS;
import static com.siimkinks.sqlitemagic.Const.ERR_TRANSFORM_TYPES_NOT_MATCHING;
import static com.siimkinks.sqlitemagic.Const.SQL_TYPE_MAP;
import static com.siimkinks.sqlitemagic.Const.SUPPORTED_SQL_BOXED_TYPES;
import static com.siimkinks.sqlitemagic.WriterUtil.hasNotNullAnnotation;

@Singleton
public class TransformerValidator {

	final Environment environment;

	@Inject
	public TransformerValidator(Environment environment) {
		this.environment = environment;
	}

	public boolean isTransformerValid(TransformerElement transformerElement) {
		final TypeElement transformerTypeElement = transformerElement.getElement();
		if (transformerTypeElement.getKind() != ElementKind.CLASS) {
			environment.error(transformerTypeElement, "Only classes can be annotated with @%s", Transformer.class.getSimpleName());
			return false;
		}
		if (transformerElement.isMissingMethods()) {
			environment.error(transformerTypeElement, "Classes annotated with @%s must have 2 static annotated transform methods -- " +
							"@%s annotated method which transforms Java objects to SQLite compatible objects and " +
							"@%s annotated method which transforms database values to Java objects",
					Transformer.class.getSimpleName(),
					ObjectToDbValue.class.getSimpleName(),
					DbValueToObject.class.getSimpleName());
			return false;
		}
		final TypeElement serializedType = transformerElement.getSerializedType().getTypeElement();
		if (serializedType == null) {
			environment.error(transformerTypeElement, "Unsupported serialization type %s", transformerElement.getRawSerializedType().toString());
			return false;
		}
		final ExtendedTypeElement deserializedType = transformerElement.getDeserializedType();
		if (deserializedType.getTypeElement() == null) {
			environment.error(transformerTypeElement, "Unsupported deserialization type %s", transformerElement.getRawDeserializedType().toString());
			return false;
		}
		final String deserializedQualifiedName = deserializedType.getQualifiedName();
		if (SQL_TYPE_MAP.get(deserializedQualifiedName) != null) {
			environment.error(transformerTypeElement, "SQL types [%s] can't have transformers",
					Joiner.on(", ").join(SUPPORTED_SQL_BOXED_TYPES));
			return false;
		}
		final String serializedTypeQualifiedName = Environment.getQualifiedName(serializedType);
		if (SQL_TYPE_MAP.get(serializedTypeQualifiedName) == null) {
			environment.error(transformerTypeElement,
					"Provided serialization type was %s, but serialized type in %s must be one of supported SQLite types - %s",
					serializedTypeQualifiedName,
					Transformer.class.getSimpleName(),
					Joiner.on(", ").join(SUPPORTED_SQL_BOXED_TYPES));
			return false;
		}
		if (environment.hasTransformerFor(deserializedType)) {
			environment.error(transformerTypeElement, ERR_MULTIPLE_TRANSFORMERS + deserializedQualifiedName);
			return false;
		}
		if (deserializedType.getTypeElement().getAnnotation(Table.class) != null) {
			environment.error(transformerTypeElement, "Cannot transform object %s which is also annotated with @%s. Delete transformer or remove annotation",
					deserializedQualifiedName, Table.class.getSimpleName());
			return false;
		}
		return true;
	}

	public static boolean isTransformerStaticMethodValid(Environment environment, ExecutableElement staticMethod, ExecutableElement prevTransformMethod) {
		if (staticMethod.getParameters().size() != 1) {
			environment.error(staticMethod, "Transformer methods must have one parameter");
			return false;
		}
		if (!staticMethod.getModifiers().contains(Modifier.STATIC)) {
			environment.error(staticMethod, "Transformer methods must be static");
			return false;
		}
		if (prevTransformMethod != null) {
			if (!assertReturnTypeSameAsFirstParamType(environment, staticMethod, prevTransformMethod)) {
				return false;
			}
			if (!assertReturnTypeSameAsFirstParamType(environment, prevTransformMethod, staticMethod)) {
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

	public boolean warnTransformationsNullabilityContracts(Map<String, TransformerElement> allTransformerElements) {
		boolean valid = true;
		for (final TransformerElement transformer : allTransformerElements.values()) {
			if (DEFAULT_TRANSFORMERS.contains(transformer.getElement().getQualifiedName().toString())) continue;
			if (!transformer.cannotTransformNullValues()) continue;

			final ExecutableElement objectToDbValue = transformer.getObjectToDbValueStaticMethod();
			final ExecutableElement dbValueToObject = transformer.getDbValueToObjectStaticMethod();
			final ReturnCallback<Boolean, Element> validator = new ReturnCallback<Boolean, Element>() {
				@Override
				public Boolean call(Element element) {
					final boolean hasAnnotation = hasNotNullAnnotation(element);
					if (!hasAnnotation) {
						environment.warning(element, "When transforming %s objects the return value cannot be null in some or all cases. " +
										"Currently transformer %s has no contract annotations fulfilling this requirement on method %s. " +
										"Please add @NonNull or @NotNull annotation and check your code.",
								transformer.getQualifiedDeserializedName(),
								transformer.getClassName(),
								element.getSimpleName().toString());
					}
					return hasAnnotation;
				}
			};
			if (!warnTransformationsNullabilityContracts(objectToDbValue, validator)) {
				valid = false;
			}
			if (!warnTransformationsNullabilityContracts(dbValueToObject, validator)) {
				valid = false;
			}
		}
		return valid;
	}

	private boolean warnTransformationsNullabilityContracts(@NonNull ExecutableElement method,
	                                                        @NonNull ReturnCallback<Boolean, Element> validator) {
		return validator.call(method);
	}
}
