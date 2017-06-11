package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.WriterUtil.FAST_CURSOR;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_NEW_INSTANCE_WITH_ONLY_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.columnOffsetParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.columnsParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.tableGraphNodeNamesParam;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RetrieveWriter implements OperationWriter {

  private final EntityEnvironment entityEnvironment;
  private final TableElement tableElement;
  private final TypeName tableElementTypeName;
  private final ClassName daoClassName;
  private final RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder;

  public static RetrieveWriter create(EntityEnvironment entityEnvironment) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    return builder()
        .entityEnvironment(entityEnvironment)
        .tableElement(entityEnvironment.getTableElement())
        .tableElementTypeName(tableElementTypeName)
        .daoClassName(entityEnvironment.getDaoClassName())
        .retrieveMethodsBodyBuilder(RetrieveMethodsBodyBuilder.create(entityEnvironment))
        .build();
  }

  @Override
  public void writeDao(TypeSpec.Builder daoClassBuilder) {
    if (tableElement.canBeInstantiatedWithOnlyId()) {
      daoClassBuilder.addMethod(newInstanceWithOnlyId(tableElement));
    }
    if (entityEnvironment.getTableElement().hasAnyPersistedComplexColumns()) {
      daoClassBuilder.addMethod(fullObjectFromCursorPosition())
          .addMethod(fullObjectFromCursorPositionWithSelection());
    }
    daoClassBuilder
        .addMethod(shallowObjectFromCursorPosition())
        .addMethod(shallowObjectFromCursorPositionWithSelection());
  }

  @Override
  public void writeHandler(TypeSpec.Builder handlerClassBuilder) {
  }

  // -------------------------------------------
  //                  DAO methods
  // -------------------------------------------

  private MethodSpec newInstanceWithOnlyId(TableElement tableElement) {
    final ColumnElement idColumn = tableElement.getIdColumn();
    final TypeName tableElementTypeName = tableElement.getTableElementTypeName();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_NEW_INSTANCE_WITH_ONLY_ID)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(idColumn.getDeserializedTypeName(), "id")
        .returns(tableElementTypeName);
    if (tableElement.isImmutable()) {
      builder.addStatement("return $L", tableElement.getValueWriter().buildOneValueSetter("id", idColumn));
    } else {
      final FormatData deserializedValueSetter = idColumn.deserializedValueSetter(ENTITY_VARIABLE, "id", MANAGER_VARIABLE);
      builder.addStatement("final $1T $2L = new $1T()", tableElementTypeName, ENTITY_VARIABLE)
          .addStatement(deserializedValueSetter.getFormat(), deserializedValueSetter.getArgs())
          .addStatement("return $L", ENTITY_VARIABLE);
    }
    return builder.build();
  }

  private MethodSpec fullObjectFromCursorPositionWithSelection() {
    return objectFromCursorPositionWithSelection(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, false);
  }

  private MethodSpec fullObjectFromCursorPosition() {
    return objectFromCursorPosition(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, false);
  }

  private MethodSpec shallowObjectFromCursorPositionWithSelection() {
    return objectFromCursorPositionWithSelection(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, true);
  }

  private MethodSpec shallowObjectFromCursorPosition() {
    return objectFromCursorPosition(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, true);
  }

  @NonNull
  private static MethodSpec.Builder objectFromCursorPositionBaseMethodBuilder(String methodName, TypeName tableElementTypeName) {
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(FAST_CURSOR, "cursor")
        .returns(tableElementTypeName);
  }

  private static MethodSpec.Builder allObjectValuesFromCursorPositionMethodBuilder(String methodName, TypeName typeName) {
    return objectFromCursorPositionBaseMethodBuilder(methodName, typeName)
        .addParameter(columnOffsetParam());
  }

  static MethodSpec.Builder selectedObjectValuesFromCursorPositionMethodBuilder(String methodName, TypeName typeName) {
    return objectFromCursorPositionBaseMethodBuilder(methodName, typeName)
        .addParameter(columnsParam())
        .addParameter(tableGraphNodeNamesParam())
        .addParameter(String.class, "nodeName");
  }

  private MethodSpec objectFromCursorPosition(String methodName, boolean shallow) {
    final MethodSpec.Builder builder = allObjectValuesFromCursorPositionMethodBuilder(methodName, tableElement.getTableElementTypeName());
    if (shallow) {
      builder.addCode(retrieveMethodsBodyBuilder.getForShallowObject());
    } else {
      builder.addCode(retrieveMethodsBodyBuilder.getForFullObject());
    }
    return builder.build();
  }

  private MethodSpec objectFromCursorPositionWithSelection(String methodName, boolean shallow) {
    final MethodSpec.Builder builder = selectedObjectValuesFromCursorPositionMethodBuilder(methodName, tableElement.getTableElementTypeName());
    if (shallow) {
      builder.addCode(retrieveMethodsBodyBuilder.getForShallowObjectFromSelection());
    } else {
      builder.addCode(retrieveMethodsBodyBuilder.getForFullObjectFromSelection());
    }
    return builder.build();
  }

  static void addValuesGatheringBlock(MethodSpec.Builder builder,
                                      boolean addDeepQuery,
                                      Callback<MethodSpec.Builder> deepBuilderCallback,
                                      Callback<MethodSpec.Builder> shallowBuilderCallback) {
    if (addDeepQuery) {
      builder.beginControlFlow("if (queryDeep)");
      deepBuilderCallback.call(builder);
      builder.nextControlFlow("else");
    }
    shallowBuilderCallback.call(builder);
    if (addDeepQuery) {
      builder.endControlFlow();
    }
  }
}
