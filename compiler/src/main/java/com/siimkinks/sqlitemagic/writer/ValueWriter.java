package com.siimkinks.sqlitemagic.writer;


import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.MethodColumnElement;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.CodeBlock;

public interface ValueWriter {
  String buildOneValueSetter(String settableVariableName, BaseColumnElement settableColumn);

  CodeBlock buildAllValuesReturningSetter(Callback settableValueCallback);

  String buildOneValueSetterFromProvidedVariable(String entityVariableName, String settableValueName, MethodColumnElement settableColumn);

  interface Callback {
    void call(CodeBlock.Builder mainBuilder, CodeBlock.Builder preCodeBuilder, BaseColumnElement columnElement, int pos, FormatData valueSetterFormat);
  }
}
