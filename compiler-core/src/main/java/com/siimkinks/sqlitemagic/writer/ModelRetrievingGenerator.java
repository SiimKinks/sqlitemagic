package com.siimkinks.sqlitemagic.writer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.LinkedList;

import static com.siimkinks.sqlitemagic.WriterUtil.SIMPLE_ARRAY_MAP;

public class ModelRetrievingGenerator implements ModelPartGenerator {
  public static final ParameterizedTypeName SYSTEM_RENAMED_TABLES_TYPE_NAME =
      ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, ClassName.get(String.class), ParameterizedTypeName.get(LinkedList.class, String.class));

  @Override
  public void write(TypeSpec.Builder daoClassBuilder, TypeSpec.Builder handlerClassBuilder, EntityEnvironment entityEnvironment) {
    final OperationWriter[] writers = new OperationWriter[]{
        QueryCompilerWriter.create(entityEnvironment),
        RetrieveWriter.create(entityEnvironment)
    };

    writeDao(daoClassBuilder, entityEnvironment);

    for (OperationWriter writer : writers) {
      writer.writeDao(daoClassBuilder);
      writer.writeHandler(handlerClassBuilder);
    }
  }

  private void writeDao(TypeSpec.Builder daoClassBuilder, EntityEnvironment entityEnvironment) {
    daoClassBuilder.addMethod(entityEnvironment.getEntityIdGetter());
  }

  static ParameterSpec systemRenamedTablesParam() {
    return ParameterSpec.builder(SYSTEM_RENAMED_TABLES_TYPE_NAME, "systemRenamedTables")
        .build();
  }
}
