package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.ViewColumn;
import com.siimkinks.sqlitemagic.annotation.ViewQuery;
import com.siimkinks.sqlitemagic.writer.EntityEnvironment;
import com.siimkinks.sqlitemagic.writer.ValueBuilderWriter;
import com.siimkinks.sqlitemagic.writer.ValueCreatorWriter;
import com.siimkinks.sqlitemagic.writer.ValueWriter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import lombok.Getter;

public final class ViewElement {
	private final Environment environment;
	@Getter
	private final TypeElement viewElement;
	@Getter
	private final View viewAnnotation;
	private final PackageElement modelPackage;
	@Getter
	private String viewName;
	@Getter
	private final TypeName viewElementTypeName;
	@Getter
	private final boolean isInterface;
	private final ImmutableSet<ExecutableElement> allMethods;
	@Getter
	private final List<ViewColumnElement> columns = new ArrayList<>();
	@Getter
	private final Set<TableElement> allTableTriggers = new HashSet<>();
	@Getter
	private VariableElement queryConstant;
	@Getter
	private int allSimpleColumnsCount = 0;
	private int columnsNeededForShallowQuery = 0;
	private int complexColumnCount = 0;
	@Getter
	private final ClassName viewClassName;
	@Getter
	private ClassName implementationClassName;
	private String abstractClassNameString;
	@Getter
	private ValueWriter valueWriter;
	private TypeElement $builderElement;

	// errors
	@Getter
	private boolean multipleQueries = false;

	public ViewElement(@NonNull Environment environment, @NonNull Element element) {
		this.environment = environment;
		this.viewElement = (TypeElement) element;
		this.viewAnnotation = element.getAnnotation(View.class);
		this.modelPackage = Environment.getPackage(element);
		this.viewName = extractViewName(viewAnnotation, viewElement);
		this.viewElementTypeName = Environment.getTypeName(this.viewElement);
		this.isInterface = viewElement.getKind() != ElementKind.CLASS;
		this.viewClassName = ClassName.get(viewElement);
		this.allMethods = environment.getLocalAndInheritedColumnMethods(viewElement);
		collectViewMetadata(viewElement);
		determineImplementationClassName();
		determineImmutabilityType(viewElement);
	}

	private static String extractViewName(@NonNull View viewAnnotation, @NonNull TypeElement viewElement) {
		final String viewName = viewAnnotation.value();
		if (Strings.isNullOrEmpty(viewName)) {
			final String rawName = viewElement.getSimpleName().toString();
			return rawName.toLowerCase();
		}
		return viewName;
	}

	private void collectViewMetadata(@NonNull TypeElement viewElement) {
		for (Element enclosedElement : viewElement.getEnclosedElements()) {
			final ViewQuery queryAnnotation = enclosedElement.getAnnotation(ViewQuery.class);
			if (queryAnnotation != null) {
				if (queryConstant != null) {
					multipleQueries = true;
				}
				queryConstant = (VariableElement) enclosedElement;
			}
		}
		for (ExecutableElement method : allMethods) {
			final ViewColumn viewColumnAnnotation = method.getAnnotation(ViewColumn.class);
			if (viewColumnAnnotation == null) {
				environment.warning(method, "Method \"%s\" is not annotated with @%s and is therefore excluded from the view creation",
						method.getSimpleName(),
						ViewColumn.class.getSimpleName());
				continue;
			}
			final ViewColumnElement viewColumnElement = ViewColumnElement.create(environment, method, viewColumnAnnotation);
			columns.add(viewColumnElement);
			if (!viewColumnElement.isComplex()) {
				allSimpleColumnsCount++;
			} else {
				allTableTriggers.addAll(viewColumnElement.getReferencedTable().getAllTableTriggers());
				complexColumnCount++;
			}
			if (!viewColumnElement.isComplex() || viewColumnElement.isNeededForShallowQuery()) {
				columnsNeededForShallowQuery++;
			}
		}
	}

	private void determineImplementationClassName() {
		final String abstractClassName;
		if (isInterface) {
			abstractClassName = String.format("%s_%sImpl",
					EntityEnvironment.getGeneratedDaoClassNameString(getViewElement()),
					getViewElementName());
		} else {
			abstractClassName = getViewElementName();
		}
		this.abstractClassNameString = abstractClassName;
		this.implementationClassName = ClassName.get(getPackageName(), environment.getValueImplementationClassNameString(abstractClassName));
	}

	private void determineImmutabilityType(TypeElement viewElement) {
		final Class<? extends Annotation> builderAnnotation = environment.getAutoValueBuilderAnnotation();
		for (Element e : viewElement.getEnclosedElements()) {
			if (e.getKind() == ElementKind.CLASS && e.getAnnotation(builderAnnotation) != null) {
				$builderElement = (TypeElement) e;
				break;
			}
		}
		if (hasBuilder()) {
			valueWriter = ValueBuilderWriter.create(environment,
					$builderElement,
					viewElement.asType(),
					columns,
					abstractClassNameString);
		} else {
			valueWriter = ValueCreatorWriter.create(environment,
					columns,
					allMethods,
					abstractClassNameString);
		}
	}

	public boolean hasBuilder() {
		return $builderElement != null;
	}

	public String getPackageName() {
		return modelPackage.getQualifiedName().toString();
	}

	public String getViewElementName() {
		return viewElement.getSimpleName().toString();
	}

	public String queryConstantName() {
		return queryConstant.getSimpleName().toString();
	}

	public int getAllColumnsCount() {
		return columns.size();
	}

	public boolean isFullQuerySameAsShallow() {
		return columnsNeededForShallowQuery == columns.size();
	}

	public boolean hasAnyComplexColumns() {
		return complexColumnCount > 0;
	}
}
