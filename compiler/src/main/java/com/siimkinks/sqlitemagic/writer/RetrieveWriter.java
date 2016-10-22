package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ALL_FROM_CURSOR;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FIRST_FROM_CURSOR;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_NEW_INSTANCE_WITH_ONLY_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.WriterUtil.ARRAY_LIST;
import static com.siimkinks.sqlitemagic.WriterUtil.FAST_CURSOR;
import static com.siimkinks.sqlitemagic.WriterUtil.MUTABLE_INT;
import static com.siimkinks.sqlitemagic.WriterUtil.codeBlockEnd;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.addLoadFromCursorMethodParams;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.columnOffsetParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.columnsParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.loadFromCursorMethodParams;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.subscriptionParam;
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
    final MethodSpec fromCurrentCursorPosition = getFromCurrentCursorPosition();
    handlerClassBuilder
        .addMethod(allFromCursor())
        .addMethod(firstFromCursor(fromCurrentCursorPosition, tableElementTypeName))
        .addMethod(fromCurrentCursorPosition);
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
  static MethodSpec.Builder objectFromCursorPositionBaseMethodBuilder(String methodName, TypeName tableElementTypeName) {
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(FAST_CURSOR, "cursor")
        .returns(tableElementTypeName);
  }

  static MethodSpec.Builder allObjectValuesFromCursorPositionMethodBuilder(String methodName, TypeName typeName) {
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

  // -------------------------------------------
  //                  Handler methods
  // -------------------------------------------

  private MethodSpec allFromCursor() {
    final ParameterizedTypeName returnType = ParameterizedTypeName.get(ARRAY_LIST, tableElementTypeName);
    final MethodSpec.Builder builder = allFromCursorBuilder(returnType)
        .beginControlFlow("if (columns == null || columns.isEmpty())")
        .addStatement("final $1T columnOffset = new $1T()", MUTABLE_INT);
    addAllValuesGatheringBlock(builder, false);
    builder.nextControlFlow("else");
    addAllValuesGatheringBlock(builder, true);
    builder.endControlFlow()
        .addStatement("return values");
    return builder.build();
  }

  @NonNull
  static MethodSpec.Builder allFromCursorBuilder(ParameterizedTypeName returnType) {
    return loadFromCursorMethodBuilder(METHOD_ALL_FROM_CURSOR, returnType)
        .addParameter(subscriptionParam())
        .addStatement("final int rowCount = cursor.getCount()")
        .beginControlFlow("if (rowCount == 0)")
        .addStatement("return new $T<>()", ARRAY_LIST)
        .endControlFlow()
        .addStatement("final $T values = new $T<>(rowCount)",
            returnType, ARRAY_LIST);
  }

  private void addAllValuesGatheringBlock(MethodSpec.Builder builder, boolean fromSelection) {
    addValuesGatheringBlock(builder, tableElement.hasAnyPersistedComplexColumns(),
        cursorRowAdder(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, daoClassName, fromSelection),
        cursorRowAdder(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, daoClassName, fromSelection));
  }

  static MethodSpec firstFromCursor(MethodSpec getFromCurrentCursorPosition, TypeName tableElementTypeName) {
    final MethodSpec.Builder builder = loadFromCursorMethodBuilder(METHOD_FIRST_FROM_CURSOR, tableElementTypeName)
        .beginControlFlow("if (!cursor.moveToFirst())")
        .addStatement("return null")
        .endControlFlow()
        .addStatement("return $N($L, null)", getFromCurrentCursorPosition, loadFromCursorMethodParams());
    return builder.build();
  }

  private MethodSpec getFromCurrentCursorPosition() {
    final MethodSpec.Builder builder = loadFromCursorMethodBuilder(METHOD_FROM_CURSOR_POSITION, tableElementTypeName)
        .addParameter(columnOffsetParam())
        .beginControlFlow("if (columns == null || columns.isEmpty())");
    addFirstValueGatheringBlock(builder, false);
    builder.nextControlFlow("else");
    addFirstValueGatheringBlock(builder, true);
    builder.endControlFlow();
    return builder.build();
  }

  private void addFirstValueGatheringBlock(MethodSpec.Builder builder, boolean fromSelection) {
    addValuesGatheringBlock(builder, tableElement.hasAnyPersistedComplexColumns(),
        cursorRowReturner(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, daoClassName, fromSelection),
        cursorRowReturner(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, daoClassName, fromSelection));
  }

  @NonNull
  static MethodSpec.Builder loadFromCursorMethodBuilder(String methodName, TypeName returnType) {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .returns(returnType);
    addLoadFromCursorMethodParams(builder);
    return builder;
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

  @NonNull
  static Callback<MethodSpec.Builder> cursorRowAdder(final String callableMethodName,
                                                     final ClassName daoClassName,
                                                     final boolean fromSelection) {
    return new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        CodeBlock.Builder codeBuilder = CodeBlock.builder()
            .add("values.add($T.$L(cursor, ",
                daoClassName,
                callableMethodName);
        if (fromSelection) {
          codeBuilder.add("columns, tableGraphNodeNames, $S", "");
        } else {
          codeBuilder.add("columnOffset");
        }
        codeBuilder.add("))")
            .add(codeBlockEnd());
        builder.beginControlFlow("while (cursor.moveToNext() && !subscription.isUnsubscribed())")
            .addCode(codeBuilder.build());
        if (!fromSelection) {
          builder.addStatement("columnOffset.value = 0");
        }
        builder.endControlFlow();
      }
    };
  }

  @NonNull
  static Callback<MethodSpec.Builder> cursorRowReturner(final String callableMethodName,
                                                        final ClassName daoClassName,
                                                        final boolean fromSelection) {
    return new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        CodeBlock.Builder codeBuilder = CodeBlock.builder()
            .add("return $T.$L(cursor, ",
                daoClassName,
                callableMethodName);
        if (fromSelection) {
          codeBuilder.add("columns, tableGraphNodeNames, $S", "");
        } else {
          codeBuilder.add("columnOffset == null ? new $T() : columnOffset", MUTABLE_INT);
        }
        codeBuilder.add(")")
            .add(codeBlockEnd());
        builder.addCode(codeBuilder.build());
      }
    };
  }
}
