package com.siimkinks.sqlitemagic.writer;

import com.squareup.javapoet.TypeSpec;

public interface OperationWriter {

	void writeDao(TypeSpec.Builder classBuilder);

	void writeHandler(TypeSpec.Builder classBuilder);
}
