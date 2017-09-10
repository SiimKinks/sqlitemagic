package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.Collections;

import javax.lang.model.element.Modifier;

import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.WriterUtil.CHECK_RESULT;
import static com.siimkinks.sqlitemagic.WriterUtil.DB_CONNECTION_IMPL;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_BULK_DELETE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_DELETE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.ENTITY_DELETE_TABLE_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.NULLABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING_BUILDER;
import static com.siimkinks.sqlitemagic.WriterUtil.addCallableToType;
import static com.siimkinks.sqlitemagic.WriterUtil.addRxSingleCreateFromCallableParentClass;
import static com.siimkinks.sqlitemagic.WriterUtil.addTableTriggersSendingStatement;
import static com.siimkinks.sqlitemagic.WriterUtil.connectionProviderMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.dbConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.dbVariableFromPresentConnectionVariable;
import static com.siimkinks.sqlitemagic.WriterUtil.getHandlerInnerClassName;
import static com.siimkinks.sqlitemagic.WriterUtil.operationBuilderInnerClassSkeleton;
import static com.siimkinks.sqlitemagic.WriterUtil.operationRxSingleMethod;
import static com.siimkinks.sqlitemagic.WriterUtil.typedCollection;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_DELETE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_DELETE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_DELETE_TABLE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_CREATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_EXECUTE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.DB_CONNECTION_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.OBJECTS_VARIABLE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

public class ModelDeletingGenerator implements ModelPartGenerator {

  @Override
  public void write(TypeSpec.Builder daoClassBuilder, TypeSpec.Builder handlerClassBuilder, EntityEnvironment entityEnvironment) {
    handlerClassBuilder
        .addType(delete(entityEnvironment))
        .addType(bulkDelete(entityEnvironment))
        .addType(deleteTable(entityEnvironment));
  }

  private TypeSpec delete(EntityEnvironment entityEnvironment) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_DELETE, ENTITY_DELETE_BUILDER, tableElementTypeName, ENTITY_VARIABLE);
    final MethodSpec deleteExecute = deleteExecute(entityEnvironment);
    builder.addSuperinterface(ENTITY_DELETE_BUILDER)
        .addMethod(deleteExecute)
        .addMethod(deleteObserve(builder, deleteExecute));
    return builder.build();
  }

  private MethodSpec deleteExecute(EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    final FormatData whereIdStatementPart = entityEnvironment.getWhereIdStatementPart();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(TypeName.INT)
        .addCode(dbConnectionVariable())
        .addCode(dbVariableFromPresentConnectionVariable())
        .addStatement(String.format("final int affectedRows = db.delete($S, %s)", whereIdStatementPart.getFormat()),
            whereIdStatementPart.getWithOtherArgsBefore(tableElement.getTableName()))
        .beginControlFlow("if (affectedRows > 0)");
    addTableTriggersSendingStatement(builder, Collections.singleton(tableElement));
    builder.endControlFlow()
        .addStatement("return affectedRows");
    return builder.build();
  }

  private MethodSpec deleteObserve(TypeSpec.Builder typeBuilder, final MethodSpec deleteExecute) {
    final TypeName entityTypeName = TypeName.INT.box();
    final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
        .addAnnotation(Override.class);
    addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {

      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addStatement("return $N()", deleteExecute);
      }
    });
    addRxSingleCreateFromCallableParentClass(builder);
    return builder.build();
  }

  private TypeSpec bulkDelete(EntityEnvironment entityEnvironment) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    final TypeName collection = typedCollection(tableElementTypeName);
    final TypeName interfaceType = ENTITY_BULK_DELETE_BUILDER;
    final MethodSpec bulkDeleteExecute = bulkDeleteExecute(entityEnvironment);
    final TypeSpec.Builder builder = operationBuilderInnerClassSkeleton(entityEnvironment, CLASS_BULK_DELETE, interfaceType, collection, OBJECTS_VARIABLE);
    return builder
        .addSuperinterface(interfaceType)
        .addMethod(bulkDeleteExecute)
        .addMethod(deleteObserve(builder, bulkDeleteExecute))
        .build();
  }

  private MethodSpec bulkDeleteExecute(EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    final String deleteStatement = "DELETE FROM " +
        tableElement.getTableName() +
        " WHERE " +
        tableElement.getIdColumn().getColumnName() +
        " IN (";
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(TypeName.INT)
        .addCode(dbConnectionVariable())
        .addCode(dbVariableFromPresentConnectionVariable())
        .addStatement("final int size = $L.size()", OBJECTS_VARIABLE)
        .addStatement("final $1T sb = new $1T(size * 2 + $2L)", STRING_BUILDER, deleteStatement.length() + 1)
        .addStatement("sb.append($S)", deleteStatement)
        .addStatement("boolean first = true")
        .beginControlFlow("for (int i = 0; i < size; i++)")
        .beginControlFlow("if (first)")
        .addStatement("first = false")
        .addStatement("sb.append(\"?\")")
        .nextControlFlow("else")
        .addStatement("sb.append(\",?\")")
        .endControlFlow()
        .endControlFlow()
        .addStatement("sb.append(\")\")")
        .addStatement("final $T stm = db.compileStatement(sb.toString())", SQLITE_STATEMENT)
        .addStatement("int i = 1")
        .beginControlFlow("for ($T $L : $L)", entityEnvironment.getTableElementTypeName(), ENTITY_VARIABLE, OBJECTS_VARIABLE);
    final CodeBlock.Builder bindIdsBuilder = CodeBlock.builder()
        .add("stm.bindLong(i, ");
    entityEnvironment.addInlineIdVariable(bindIdsBuilder);
    bindIdsBuilder.add(");\n");
    builder.addCode(bindIdsBuilder.build())
        .addStatement("i++")
        .endControlFlow()
        .addStatement("final int affectedRows = stm.executeUpdateDelete()")
        .beginControlFlow("if (affectedRows > 0)");
    addTableTriggersSendingStatement(builder, Collections.singleton(tableElement));
    builder.endControlFlow()
        .addStatement("return affectedRows");
    return builder.build();
  }

  private TypeSpec deleteTable(EntityEnvironment entityEnvironment) {
    final String className = CLASS_DELETE_TABLE;
    final TypeName createdClassTypeName = getHandlerInnerClassName(entityEnvironment, className);
    final MethodSpec deleteTableExecute = deleteTableExecute(entityEnvironment);
    final TypeSpec.Builder builder = TypeSpec.classBuilder(className);
    builder.addSuperinterface(ENTITY_DELETE_TABLE_BUILDER)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .addField(FieldSpec.builder(DB_CONNECTION_IMPL, DB_CONNECTION_VARIABLE, PRIVATE)
            .addAnnotation(NULLABLE)
            .build())
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build())
        .addMethod(WriterUtil.createMagicInvokableMethod(EntityEnvironment.getGeneratedHandlerInnerClassName(entityEnvironment.getTableElement(), className), METHOD_CREATE)
            .addModifiers(STATIC_METHOD_MODIFIERS)
            .addAnnotation(CHECK_RESULT)
            .addStatement("return new $N()", className)
            .returns(createdClassTypeName)
            .build())
        .addMethod(connectionProviderMethod(ENTITY_DELETE_TABLE_BUILDER))
        .addMethod(deleteTableExecute)
        .addMethod(deleteTableObserve(builder, deleteTableExecute));
    return builder.build();
  }

  private MethodSpec deleteTableExecute(EntityEnvironment entityEnvironment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_EXECUTE)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(TypeName.INT)
        .addCode(dbConnectionVariable())
        .addCode(dbVariableFromPresentConnectionVariable())
        .addStatement("final $T affectedRows = db.delete($S, \"1\", null)", TypeName.INT, tableElement.getTableName())
        .beginControlFlow("if (affectedRows > 0)");
    addTableTriggersSendingStatement(builder, Collections.singleton(tableElement));
    builder.endControlFlow()
        .addStatement("return affectedRows");
    return builder.build();
  }

  private MethodSpec deleteTableObserve(TypeSpec.Builder typeBuilder, final MethodSpec deleteTableExecute) {
    final TypeName entityTypeName = TypeName.INT.box();
    final MethodSpec.Builder builder = operationRxSingleMethod(entityTypeName)
        .addAnnotation(Override.class);
    addCallableToType(typeBuilder, entityTypeName, new Callback<MethodSpec.Builder>() {
      @Override
      public void call(MethodSpec.Builder builder) {
        builder.addStatement("return $N()", deleteTableExecute);
      }
    });
    addRxSingleCreateFromCallableParentClass(builder);
    return builder.build();
  }
}
