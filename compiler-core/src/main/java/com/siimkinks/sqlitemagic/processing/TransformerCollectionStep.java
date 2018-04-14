package com.siimkinks.sqlitemagic.processing;

import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Database;
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.util.ConditionCallback;
import com.siimkinks.sqlitemagic.validator.TransformerValidator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static java.util.Collections.emptyList;

public class TransformerCollectionStep implements ProcessingStep {

  @Inject
  Environment environment;
  @Inject
  TransformerValidator validator;

  public TransformerCollectionStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean isSuccessfulProcessing = true;
    final Map<String, TransformerElement> transformers = new HashMap<>();

    final Set<? extends Element> objectToDbValueElements = roundEnv.getElementsAnnotatedWith(ObjectToDbValue.class);
    final Set<? extends Element> dbValueToObjectElements = roundEnv.getElementsAnnotatedWith(DbValueToObject.class);
    collectTransformers(
        objectToDbValueElements,
        dbValueToObjectElements,
        transformers,
        false
    );

    final List<TypeElement> externalTransformers = getExternalTransformers(roundEnv);
    final Set<ExecutableElement> methods = getAllStaticMethods(externalTransformers);
    collectTransformers(
        filterMethodsWithAnnotation(methods, ObjectToDbValue.class),
        filterMethodsWithAnnotation(methods, DbValueToObject.class),
        transformers,
        true
    );

    for (TransformerElement transformer : transformers.values()) {
      if (!validator.isTransformerValid(transformer)) {
        isSuccessfulProcessing = false;
      } else {
        environment.addTransformerElement(transformer);
      }
    }
    return isSuccessfulProcessing;
  }

  private void collectTransformers(
      Set<? extends Element> objectToDbValueElements,
      Set<? extends Element> dbValueToObjectElements,
      Map<String, TransformerElement> transformers,
      boolean external
  ) {
    for (Element element : objectToDbValueElements) {
      final ExecutableElement objectToDbValue = (ExecutableElement) element;
      final TransformerElement transformerElement = TransformerElement.fromObjectToDbValue(environment, objectToDbValue, external);
      transformers.put(transformerElement.getRawDeserializedType().toString(), transformerElement);
    }

    for (Element element : dbValueToObjectElements) {
      final ExecutableElement dbValueToObject = (ExecutableElement) element;
      final TypeMirror rawDeserializedType = dbValueToObject.getReturnType();
      final TransformerElement transformer = transformers.get(rawDeserializedType.toString());
      if (transformer != null) {
        transformer.addDbValueToObjectMethod(dbValueToObject);
      } else {
        final TransformerElement incompleteTransformer = TransformerElement.fromDbValueToObject(environment, dbValueToObject, external);
        transformers.put(incompleteTransformer.getRawDeserializedType().toString(), incompleteTransformer);
      }
    }
  }

  private List<TypeElement> getExternalTransformers(RoundEnvironment roundEnv) {
    final Elements elementUtils = environment.getElementUtils();
    final List<TypeElement> result = new ArrayList<>();
    final Class<?>[] externalTransformers;
    try {
      if (environment.isSubmodule()) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SubmoduleDatabase.class);
        if (elements.isEmpty()) {
          return emptyList();
        }
        final Element element = elements.iterator().next();
        final SubmoduleDatabase annotation = element.getAnnotation(SubmoduleDatabase.class);
        externalTransformers = annotation.externalTransformers();
      } else {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Database.class);
        if (elements.isEmpty()) {
          return emptyList();
        }
        final Element element = elements.iterator().next();
        final Database annotation = element.getAnnotation(Database.class);
        externalTransformers = annotation.externalTransformers();
      }
    } catch (MirroredTypesException e) {
      for (TypeMirror typeMirror : e.getTypeMirrors()) {
        final TypeElement typeElement = elementUtils.getTypeElement(typeMirror.toString());
        result.add(typeElement);
      }
      return result;
    }
    for (Class<?> clazz : externalTransformers) {
      final TypeElement typeElement = environment.getTypeElement(clazz);
      result.add(typeElement);
    }
    return result;
  }

  private Set<ExecutableElement> getAllStaticMethods(List<TypeElement> externalTransformers) {
    final HashSet<ExecutableElement> result = new HashSet<>();
    for (TypeElement typeElement : externalTransformers) {
      final Set<ExecutableElement> methods = environment.getLocalAndInheritedMethods(typeElement, new ConditionCallback<ExecutableElement>() {
        @Override
        public boolean call(ExecutableElement method) {
          return method.getModifiers().contains(Modifier.STATIC);
        }
      });
      result.addAll(methods);
    }
    return result;
  }

  private Set<ExecutableElement> filterMethodsWithAnnotation(Set<ExecutableElement> methods, Class<? extends Annotation> annotation) {
    final HashSet<ExecutableElement> result = new HashSet<>();
    for (ExecutableElement method : methods) {
      if (method.getAnnotation(annotation) != null) {
        result.add(method);
      }
    }
    return result;
  }
}
