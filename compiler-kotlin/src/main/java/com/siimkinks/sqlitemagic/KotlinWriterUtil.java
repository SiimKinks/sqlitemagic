package com.siimkinks.sqlitemagic;

import com.squareup.kotlinpoet.TypeName;

import javax.lang.model.type.TypeMirror;

import io.reactivex.annotations.NonNull;

// FIXME remove when kotlinpoet correctly implements extension functions
public class KotlinWriterUtil {
  public static TypeName get(@NonNull TypeMirror typeMirror) {
    return TypeName.get(typeMirror);
  }
}