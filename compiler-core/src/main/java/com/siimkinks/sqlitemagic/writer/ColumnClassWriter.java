package com.siimkinks.sqlitemagic.writer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;

import static com.siimkinks.sqlitemagic.Const.CLASS_MODIFIERS;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.COMPLEX_COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.CURSOR;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.NULLABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.NUMERIC_COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING;
import static com.siimkinks.sqlitemagic.WriterUtil.SUPPORT_SQLITE_STATEMENT;
import static com.siimkinks.sqlitemagic.WriterUtil.TABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.UNIQUE;
import static com.siimkinks.sqlitemagic.WriterUtil.VALUE_PARSER;
import static com.siimkinks.sqlitemagic.WriterUtil.notNullParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.nullableParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.writeSource;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static javax.lang.model.element.Modifier.PUBLIC;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ColumnClassWriter {
  public static final String VAL_VARIABLE = "val";

  private final Environment environment;
  private final String className;
  private final TypeName superClass;
  private final TypeName deserializedTypeName;
  private final TypeVariableName parentTableType;
  private final TypeVariableName nullabilityType;
  @Nullable
  private final CodeBlock initBlock;
  private final ExtendedTypeElement serializedType;
  private final FormatData valueGetter;
  @Nullable
  private final TransformerElement transformerElement;
  private final boolean nullable;
  private final boolean unique;

  public static ColumnClassWriter from(@NonNull TransformerElement transformerElement,
                                       @NonNull Environment environment,
                                       boolean createUniqueClass) {
    final TypeName deserializedTypeName = transformerElement.getDeserializedTypeNameForGenerics();
    final ClassName superClassName = transformerElement.isNumericType() ? NUMERIC_COLUMN : COLUMN;
    final TypeVariableName parentTableType = TypeVariableName.get("T");
    final TypeVariableName nullabilityType = TypeVariableName.get("N");
    final ExtendedTypeElement serializedType = transformerElement.getSerializedType();
    final String className = createUniqueClass ? getUniqueClassName(transformerElement) : getClassName(transformerElement);

    return ColumnClassWriter.builder()
        .environment(environment)
        .className(className)
        .deserializedTypeName(deserializedTypeName)
        .serializedType(serializedType)
        .superClass(ParameterizedTypeName.get(superClassName,
            deserializedTypeName, deserializedTypeName, deserializedTypeName,
            parentTableType, nullabilityType))
        .parentTableType(parentTableType)
        .nullabilityType(nullabilityType)
        .valueGetter(transformerElement.serializedValueGetter(VAL_VARIABLE))
        .transformerElement(transformerElement)
        .nullable(!serializedType.isPrimitiveElement())
        .unique(createUniqueClass)
        .build();
  }

  public static ColumnClassWriter from(@NonNull TableElement tableElement,
                                       @NonNull Environment environment,
                                       boolean createUniqueClass) {
    final ColumnElement idColumn = tableElement.getIdColumn();
    final TypeName deserializedTypeName = tableElement.getTableElementTypeName();
    final TypeName serializedTypeName = idColumn.getSerializedTypeNameForGenerics();
    final TypeVariableName parentTableType = TypeVariableName.get("T");
    final TypeVariableName nullabilityType = TypeVariableName.get("N");
    final String className = createUniqueClass ? getUniqueClassName(tableElement) : getClassName(tableElement);

    final ColumnClassWriterBuilder builder = ColumnClassWriter.builder()
        .environment(environment)
        .className(className)
        .superClass(ParameterizedTypeName.get(COMPLEX_COLUMN,
            deserializedTypeName, serializedTypeName, idColumn.getEquivalentType(),
            parentTableType, nullabilityType))
        .parentTableType(parentTableType)
        .nullabilityType(nullabilityType)
        .deserializedTypeName(deserializedTypeName)
        .serializedType(idColumn.getSerializedType())
        .valueGetter(tableElement.serializedValueGetter(VAL_VARIABLE))
        .nullable(idColumn.isNullable())
        .unique(createUniqueClass);
    return builder.build();
  }

  public TypeSpec write(@NonNull Filer filer) throws IOException {
    final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
        .addModifiers(CLASS_MODIFIERS)
        .addTypeVariable(parentTableType)
        .addTypeVariable(nullabilityType)
        .superclass(superClass)
        .addMethod(constructor())
        .addMethod(toSqlArg())
        .addMethod(aliasOverride());
    if (transformerElement != null) {
      classBuilder.addMethod(cursorParserOverride(transformerElement))
          .addMethod(statementParserOverride(transformerElement));
    }
    if (unique) {
      classBuilder.addSuperinterface(ParameterizedTypeName.get(UNIQUE, nullabilityType));
    }
    final TypeSpec type = classBuilder.build();
    final TypeElement typeElement = environment.getElementUtils().getTypeElement(PACKAGE_ROOT + "." + className);
    // write source only if there isn't already this type
    if (typeElement == null) {
      writeSource(filer, type);
    }
    return type;
  }

  @NonNull
  private MethodSpec constructor() {
    return MethodSpec.constructorBuilder()
        .addParameters(Arrays.asList(
            notNullParameter(ParameterizedTypeName.get(TABLE, parentTableType), "table"),
            notNullParameter(String.class, "name"),
            notNullParameter(VALUE_PARSER, "valueParser"),
            ParameterSpec.builder(TypeName.BOOLEAN, "nullable").build(),
            nullableParameter(STRING, "alias")
        ))
        .addStatement("super(table, name, false, valueParser, nullable, alias)")
        .build();
  }

  @NonNull
  private MethodSpec toSqlArg() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder("toSqlArg")
        .addAnnotation(NON_NULL)
        .addAnnotation(Override.class)
        .addParameter(notNullParameter(deserializedTypeName, VAL_VARIABLE))
        .returns(String.class);
    if (initBlock != null) {
      builder.addCode(initBlock);
    }
    if (nullable) {
      builder.addStatement(String.format("final $T sqlVal = %s", valueGetter.getFormat()),
          valueGetter.getWithOtherArgsBefore(serializedType.getTypeElement()))
          .beginControlFlow("if (sqlVal == null)")
          .addStatement("throw new $T($S)", NullPointerException.class, "SQL argument cannot be null")
          .endControlFlow();
      if (serializedType.isPrimitiveElement()) {
        final TypeName boxedType = TypeName.get(serializedType.getTypeMirror()).box();
        builder.addStatement("return $T.toString(sqlVal)", boxedType);
      } else if (serializedType.isStringType(environment)) {
        builder.addStatement("return sqlVal");
      } else {
        builder.addStatement("return sqlVal.toString()");
      }
    } else {
      if (serializedType.isPrimitiveElement()) {
        final TypeName boxedType = TypeName.get(serializedType.getTypeMirror()).box();
        builder.addStatement(String.format("return $T.toString(%s)", valueGetter.getFormat()),
            valueGetter.getWithOtherArgsBefore(boxedType));
      } else if (serializedType.isStringType(environment)) {
        builder.addStatement(String.format("return %s", valueGetter.getFormat()),
            valueGetter.getArgs());
      } else {
        builder.addStatement(String.format("return %s.toString()", valueGetter.getFormat()),
            valueGetter.getArgs());
      }
    }
    return builder.build();
  }

  @NonNull
  private MethodSpec aliasOverride() {
    final TypeName classType = ParameterizedTypeName.get(ClassName.get(PACKAGE_ROOT, className),
        parentTableType, nullabilityType);
    return MethodSpec.methodBuilder("as")
        .addAnnotation(NON_NULL)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(classType)
        .addParameter(notNullParameter(STRING, "alias"))
        .addStatement("return new $T(table, name, valueParser, nullable, alias)", classType)
        .build();
  }

  @NonNull
  private MethodSpec cursorParserOverride(@NonNull TransformerElement transformerElement) {
    final TypeVariableName returnType = TypeVariableName.get("V");
    final MethodSpec.Builder builder = MethodSpec.methodBuilder("getFromCursor")
        .addAnnotation(NULLABLE)
        .addAnnotation(Override.class)
        .addParameter(notNullParameter(CURSOR, "cursor"))
        .addTypeVariable(returnType)
        .returns(returnType)
        .addStatement("final $T dbVal = super.getFromCursor(cursor)", transformerElement.getSerializedTypeName());
    final FormatData valGetter = transformerElement.deserializedValueGetter("dbVal");
    builder.addStatement(valGetter.formatInto("return ($T) %s"), valGetter.getWithOtherArgsBefore(returnType));
    return builder.build();
  }

  @NonNull
  private MethodSpec statementParserOverride(@NonNull TransformerElement transformerElement) {
    final TypeVariableName returnType = TypeVariableName.get("V");
    final MethodSpec.Builder builder = MethodSpec.methodBuilder("getFromStatement")
        .addAnnotation(NULLABLE)
        .addAnnotation(Override.class)
        .addParameter(notNullParameter(SUPPORT_SQLITE_STATEMENT, "stm"))
        .addTypeVariable(returnType)
        .returns(returnType)
        .addStatement("final $T dbVal = super.getFromStatement(stm)", transformerElement.getSerializedTypeName());
    final FormatData valGetter = transformerElement.deserializedValueGetter("dbVal");
    builder.addStatement(valGetter.formatInto("return ($T) %s"), valGetter.getWithOtherArgsBefore(returnType));
    return builder.build();
  }

  @NonNull
  public static String getClassName(@NonNull TransformerElement transformerElement) {
    return transformerElement.getTransformerName() + "Column";
  }

  @NonNull
  public static String getUniqueClassName(@NonNull TransformerElement transformerElement) {
    return "Unique" + getClassName(transformerElement);
  }

  @NonNull
  public static String getClassName(@NonNull TableElement tableElement) {
    return tableElement.getTableElementName() + "Column";
  }

  @NonNull
  public static String getUniqueClassName(@NonNull TableElement tableElement) {
    return "Unique" + getClassName(tableElement);
  }
}
