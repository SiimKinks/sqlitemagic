package com.siimkinks.sqlitemagic.writer;

import com.squareup.javapoet.TypeSpec;

public interface ModelPartGenerator {
  void write(TypeSpec.Builder daoClassBuilder, TypeSpec.Builder handlerClassBuilder, EntityEnvironment entityEnvironment);
}
