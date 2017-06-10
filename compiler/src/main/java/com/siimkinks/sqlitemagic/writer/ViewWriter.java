package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;

import static com.siimkinks.sqlitemagic.Const.CLASS_MODIFIERS;
import static com.siimkinks.sqlitemagic.Const.INNER_ABSTRACT_CLASS_MODIFIERS;
import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.WriterUtil.COMPILED_N_COLUMNS_SELECT;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_VIEW_QUERY;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedDaoClassNameString;
import static com.siimkinks.sqlitemagic.writer.RetrieveWriter.selectedObjectValuesFromCursorPositionMethodBuilder;

@Singleton
public final class ViewWriter {
  private final Environment environment;

  @Inject
  public ViewWriter(Environment environment) {
    this.environment = environment;
  }

  public void writeSource(Filer filer, ViewElement viewElement) throws IOException {
    final boolean isFullQueryNeeded = !viewElement.isFullQuerySameAsShallow();
    final RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder = RetrieveMethodsBodyBuilder.create(viewElement);

    final TypeSpec.Builder daoClassBuilder = TypeSpec.classBuilder(getGeneratedDaoClassNameString(viewElement.getViewElement()))
        .addModifiers(CLASS_MODIFIERS)
        .addField(schemaConstant(viewElement))
        .addMethod(shallowObjectFromCursorPosition(viewElement, retrieveMethodsBodyBuilder));

    if (isFullQueryNeeded) {
      daoClassBuilder.addMethod(fullObjectFromCursorPosition(viewElement, retrieveMethodsBodyBuilder));
    }

    if (viewElement.isInterface()) {
      daoClassBuilder.addType(interfaceImplementation(viewElement));
    }

    WriterUtil.writeSource(filer, daoClassBuilder.build(), viewElement.getPackageName());

    StructureWriter.from(viewElement, environment).write(filer);
  }

  private FieldSpec schemaConstant(ViewElement viewElement) {
    return FieldSpec.builder(COMPILED_N_COLUMNS_SELECT, FIELD_VIEW_QUERY, PUBLIC_STATIC_FINAL)
        .initializer("$T.$L", viewElement.getViewClassName(), viewElement.queryConstantName())
        .build();
  }

  private TypeSpec interfaceImplementation(ViewElement viewElement) {
    return TypeSpec.classBuilder(String.format("%sImpl", viewElement.getViewElementName()))
        .addModifiers(INNER_ABSTRACT_CLASS_MODIFIERS)
        .addSuperinterface(Environment.getTypeName(viewElement.getViewElement()))
        .addAnnotation(environment.getAutoValueAnnotation())
        .build();
  }

  private MethodSpec shallowObjectFromCursorPosition(ViewElement viewElement, RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder) {
    return selectedObjectValuesFromCursorPositionMethodBuilder(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, viewElement.getViewElementTypeName())
        .addCode(retrieveMethodsBodyBuilder.getForShallowObjectForViewWithSelection())
        .build();
  }

  private MethodSpec fullObjectFromCursorPosition(ViewElement viewElement, RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder) {
    return selectedObjectValuesFromCursorPositionMethodBuilder(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, viewElement.getViewElementTypeName())
        .addCode(retrieveMethodsBodyBuilder.getForFullObjectForViewWithSelection())
        .build();
  }
}
