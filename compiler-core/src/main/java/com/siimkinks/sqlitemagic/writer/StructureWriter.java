package com.siimkinks.sqlitemagic.writer;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.Const.PUBLIC_FINAL;
import static com.siimkinks.sqlitemagic.Const.PUBLIC_STATIC_FINAL;
import static com.siimkinks.sqlitemagic.WriterUtil.ARRAY_LIST;
import static com.siimkinks.sqlitemagic.WriterUtil.COLLECTIONS;
import static com.siimkinks.sqlitemagic.WriterUtil.COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.COMPILED_N_COLUMNS_SELECT_IMPL;
import static com.siimkinks.sqlitemagic.WriterUtil.FAST_CURSOR;
import static com.siimkinks.sqlitemagic.WriterUtil.MAPPER;
import static com.siimkinks.sqlitemagic.WriterUtil.MAPPER_WITH_COLUMN_OFFSET;
import static com.siimkinks.sqlitemagic.WriterUtil.NON_NULL;
import static com.siimkinks.sqlitemagic.WriterUtil.NULLABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.NUMERIC_COLUMN;
import static com.siimkinks.sqlitemagic.WriterUtil.SIMPLE_ARRAY_MAP;
import static com.siimkinks.sqlitemagic.WriterUtil.STRING;
import static com.siimkinks.sqlitemagic.WriterUtil.TABLE;
import static com.siimkinks.sqlitemagic.WriterUtil.UTIL;
import static com.siimkinks.sqlitemagic.WriterUtil.notNullParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.nullableParameter;
import static com.siimkinks.sqlitemagic.WriterUtil.writeSource;
import static com.siimkinks.sqlitemagic.util.NameConst.FIELD_VIEW_QUERY;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_DEEP_QUERY_PARTS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_ADD_SHALLOW_QUERY_PARTS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_MAPPER;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.util.StringUtil.replaceCamelCaseWithUnderscore;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedDaoClassName;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedTableStructureInterfaceNameString;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.columnsParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.fromSelectClauseParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.select1Param;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.selectFromTablesParam;
import static com.siimkinks.sqlitemagic.writer.GenClassesManagerWriter.tableGraphNodeNamesParam;
import static com.siimkinks.sqlitemagic.writer.QueryCompilerWriter.queryPartsAddMethodSignature;
import static com.siimkinks.sqlitemagic.writer.RetrieveWriter.addValuesGatheringBlock;
import static com.squareup.javapoet.TypeName.BOOLEAN;
import static com.squareup.javapoet.TypeName.INT;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class StructureWriter {
  private final String className;
  private final ClassName classType;
  private final Environment environment;
  private final String structureElementName;
  private final TypeName structureElementTypeName;
  private final String structureName;
  private final int columnsCount;
  private final List<BaseColumnElement> columns;
  private final Set<TableElement> allTableTriggers;
  private final ClassName handlerClassName;
  private final ClassName daoClassName;
  private final boolean hasAnyPersistedComplexColumns;
  private final boolean isQueryPartNeededForShallowQuery;
  private final boolean isFullQueryNeeded;
  private final boolean isOffsetQueryNeeded;
  private final boolean isView;

  public static StructureWriter from(@NonNull EntityEnvironment entityEnvironment,
                                     @NonNull Environment environment) {
    final TableElement tableElement = entityEnvironment.getTableElement();
    final String className = getGeneratedTableStructureInterfaceNameString(tableElement);
    return builder()
        .className(className)
        .classType(ClassName.get(PACKAGE_ROOT, className))
        .structureElementName(tableElement.getTableElementName())
        .structureElementTypeName(tableElement.getTableElementTypeName())
        .structureName(tableElement.getTableName())
        .columnsCount(tableElement.getAllColumnsCount())
        .columns(new ArrayList<BaseColumnElement>(tableElement.getAllColumns()))
        .allTableTriggers(tableElement.getAllTableTriggers())
        .daoClassName(entityEnvironment.getDaoClassName())
        .handlerClassName(entityEnvironment.getHandlerClassName())
        .hasAnyPersistedComplexColumns(tableElement.hasAnyPersistedComplexColumns())
        .isQueryPartNeededForShallowQuery(tableElement.isQueryPartNeededForShallowQuery())
        .isFullQueryNeeded(tableElement.hasAnyPersistedComplexColumns())
        .isOffsetQueryNeeded(true)
        .environment(environment)
        .build();
  }

  public static StructureWriter from(@NonNull ViewElement viewElement,
                                     @NonNull Environment environment) {
    final String className = getGeneratedTableStructureInterfaceNameString(viewElement.getViewElementName());
    final ClassName generatedDaoClassName = getGeneratedDaoClassName(viewElement);
    return builder()
        .className(className)
        .classType(ClassName.get(PACKAGE_ROOT, className))
        .environment(environment)
        .structureElementName(viewElement.getViewElementName())
        .structureElementTypeName(viewElement.getViewElementTypeName())
        .structureName(viewElement.getViewName())
        .columnsCount(viewElement.getAllColumnsCount())
        .columns(new ArrayList<BaseColumnElement>(viewElement.getColumns()))
        .allTableTriggers(viewElement.getAllTableTriggers())
        .daoClassName(generatedDaoClassName)
        .handlerClassName(generatedDaoClassName)
        .hasAnyPersistedComplexColumns(viewElement.hasAnyComplexColumns())
        .isFullQueryNeeded(!viewElement.isFullQuerySameAsShallow())
        .isOffsetQueryNeeded(false)
        .isView(true)
        .build();
  }

  public void write(@NonNull Filer filer) throws IOException {
    final TypeSpec.Builder classBuilder = classBuilder(className)
        .addModifiers(PUBLIC_FINAL)
        .superclass(ParameterizedTypeName.get(TABLE, structureElementTypeName))
        .addMethod(constructor())
        .addField(structureField())
        .addFields(columnFields())
        .addMethod(aliasOverride())
        .addMethod(mapper());
    if (hasAnyPersistedComplexColumns && !isView) {
      classBuilder.addMethod(queryPartsAddOverride(METHOD_ADD_DEEP_QUERY_PARTS));
      if (isQueryPartNeededForShallowQuery) {
        classBuilder.addMethod(queryPartsAddOverride(METHOD_ADD_SHALLOW_QUERY_PARTS));
      }
    }
    if (isView) {
      classBuilder.addMethod(perfectSelectionOverride());
    }
    writeSource(filer, classBuilder.build());
  }

  private MethodSpec constructor() {
    return MethodSpec.constructorBuilder()
        .addModifiers(PRIVATE)
        .addParameter(notNullParameter(String.class, "alias"))
        .addStatement("super($S, alias, $L)",
            structureName, columnsCount)
        .build();
  }

  private FieldSpec structureField() {
    final String fieldName = structureFieldName(structureElementName);
    return FieldSpec.builder(classType, fieldName)
        .addModifiers(PUBLIC_STATIC_FINAL)
        .initializer("new $T(null)", classType)
        .build();
  }

  @NonNull
  public static String structureFieldName(@NonNull String elementName) {
    return replaceCamelCaseWithUnderscore(elementName).toUpperCase();
  }

  @NonNull
  public static String structureFieldName(@NonNull TableElement tableElement) {
    return structureFieldName(tableElement.getTableElementName());
  }

  private Iterable<FieldSpec> columnFields() {
    final TypeName structureElementTypeName = this.structureElementTypeName;
    final ArrayList<FieldSpec> columnFields = new ArrayList<>(columnsCount);
    final HashSet<String> generatedColumnNames = new HashSet<>();

    for (BaseColumnElement columnElement : columns) {
      final TypeName columnImplType;
      FormatData cursorGetter = FormatData.create(", $T." + columnElement.cursorParserConstantName(environment), UTIL);
      if (columnElement.hasTransformer()) {
        columnImplType = ParameterizedTypeName.get(ClassName.get(PACKAGE_ROOT, ColumnClassWriter.getClassName(columnElement.getTransformer())), structureElementTypeName);
      } else if (columnElement.isReferencedColumn()) {
        if (isView)
          continue; // do not support complex columns in views -- no way to reference them in queries

        columnImplType = ParameterizedTypeName.get(ClassName.get(PACKAGE_ROOT, ColumnClassWriter.getClassName(columnElement.getReferencedTable())), structureElementTypeName);
      } else {
        final ClassName columnClass = columnElement.isNumericType() ? NUMERIC_COLUMN : COLUMN;
        final TypeName columnDeserializedType = columnElement.getDeserializedTypeNameForGenerics();
        final TypeName columnSerializedType = columnElement.getSerializedTypeNameForGenerics();
        columnImplType = ParameterizedTypeName.get(columnClass,
            columnDeserializedType, columnSerializedType, columnElement.getEquivalentType(), structureElementTypeName);
        cursorGetter = FormatData.create(", false, $T." + columnElement.cursorParserConstantName(environment), UTIL);
      }
      final String fieldName = columnFieldName(columnElement);
      final String columnName = getColumnName(columnElement);
      if (generatedColumnNames.contains(columnName)) {
        throw new IllegalStateException("Ambiguous column name \"" + columnElement.getColumnName() + "\"");
      }
      generatedColumnNames.add(columnName);
      columnFields.add(FieldSpec
          .builder(columnImplType, fieldName)
          .addModifiers(PUBLIC_FINAL)
          .initializer(cursorGetter.formatInto("new $T(this, $S%s, $L, null)"),
              cursorGetter.getArgsBetween(columnImplType, columnName)
                  .and(columnElement.isNullable()))
          .build());
    }
    return columnFields;
  }

  private String getColumnName(BaseColumnElement columnElement) {
    final String columnName = columnElement.getColumnName();
    if (isView) {
      final int dotPos = columnName.lastIndexOf('.');
      if (dotPos != -1) {
        return columnName.substring(dotPos + 1);
      }
    }
    return columnName;
  }

  @NonNull
  public static String columnFieldName(@NonNull BaseColumnElement columnElement) {
    return columnElement.getColumnName()
        .toUpperCase()
        .replaceAll("\\.", "_");
  }

  private MethodSpec aliasOverride() {
    return MethodSpec.methodBuilder("as")
        .addAnnotation(NON_NULL)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(classType)
        .addParameter(notNullParameter(String.class, "alias"))
        .addStatement("return new $T(alias)", classType)
        .build();
  }

  private MethodSpec mapper() {
    final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_MAPPER)
        .addAnnotation(Override.class)
        .addAnnotation(NON_NULL)
        .addParameter(columnsParam())
        .addParameter(tableGraphNodeNamesParam())
        .addParameter(boolean.class, "queryDeep")
        .returns(ParameterizedTypeName.get(MAPPER, structureElementTypeName));

    if (isOffsetQueryNeeded) {
      builder.beginControlFlow("if (columns == null || columns.isEmpty())");
      addValuesGatheringBlock(builder, isFullQueryNeeded,
          new Callback<MethodSpec.Builder>() {
            @Override
            public void call(MethodSpec.Builder deepBuilder) {
              deepBuilder.addStatement("return $L", mapperFunction(true, CodeBlock.builder()
                  .addStatement("columnOffset.value = 0")
                  .addStatement("return $T.$L(cursor, columnOffset)",
                      daoClassName,
                      METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
                  .build()));
            }
          },
          new Callback<MethodSpec.Builder>() {
            @Override
            public void call(MethodSpec.Builder shallowBuilder) {
              shallowBuilder.addStatement("return $L", mapperFunction(true, CodeBlock.builder()
                  .addStatement("columnOffset.value = 0")
                  .addStatement("return $T.$L(cursor, columnOffset)",
                      daoClassName,
                      METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
                  .build()));
            }
          });
      builder.nextControlFlow("else");
    }

    addValuesGatheringBlock(builder, isFullQueryNeeded,
        new Callback<MethodSpec.Builder>() {
          @Override
          public void call(MethodSpec.Builder deepBuilder) {
            deepBuilder.addStatement("return $L", mapperFunction(false, CodeBlock.builder()
                .addStatement("return $T.$L(cursor, columns, tableGraphNodeNames, \"\")",
                    daoClassName,
                    METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
                .build()));
          }
        },
        new Callback<MethodSpec.Builder>() {
          @Override
          public void call(MethodSpec.Builder shallowBuilder) {
            shallowBuilder.addStatement("return $L", mapperFunction(false, CodeBlock.builder()
                .addStatement("return $T.$L(cursor, columns, tableGraphNodeNames, \"\")",
                    daoClassName,
                    METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
                .build()));
          }
        });

    if (isOffsetQueryNeeded) {
      builder.endControlFlow();
    }
    return builder.build();
  }

  private TypeSpec mapperFunction(boolean withColumnOffset, @NonNull CodeBlock functionBody) {
    return TypeSpec
        .anonymousClassBuilder("")
        .addSuperinterface(ParameterizedTypeName.get(withColumnOffset ? MAPPER_WITH_COLUMN_OFFSET : MAPPER, structureElementTypeName))
        .addMethod(MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(FAST_CURSOR, "cursor")
            .returns(structureElementTypeName)
            .addCode(functionBody)
            .build())
        .build();
  }

  private MethodSpec queryPartsAddOverride(@NonNull String methodName) {
    return queryPartsAddMethodSignature(methodName)
        .addAnnotation(Override.class)
        .addAnnotation(NULLABLE)
        .addStatement("return $T.$L($L, $L, $L, $L)", handlerClassName, methodName,
            fromSelectClauseParam().name, selectFromTablesParam().name, tableGraphNodeNamesParam().name, select1Param().name)
        .build();
  }

  private MethodSpec perfectSelectionOverride() {
    return MethodSpec.methodBuilder("perfectSelection")
        .returns(BOOLEAN)
        .addParameter(notNullParameter(ParameterizedTypeName.get(ARRAY_LIST, STRING), "observedTables"))
        .addParameter(nullableParameter(ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, STRING, STRING), "tableGraphNodeNames"))
        .addParameter(nullableParameter(ParameterizedTypeName.get(SIMPLE_ARRAY_MAP, STRING, INT.box()), "columnPositions"))
        .addAnnotation(Override.class)
        .addStatement("final $1T query = ($1T) $2T.$3L",
            COMPILED_N_COLUMNS_SELECT_IMPL,
            handlerClassName,
            FIELD_VIEW_QUERY)
        .addStatement("$T.addAll(observedTables, query.observedTables)", COLLECTIONS)
        .beginControlFlow("if (tableGraphNodeNames != null)")
        .addStatement("tableGraphNodeNames.putAll(query.tableGraphNodeNames)")
        .endControlFlow()
        .beginControlFlow("if (columnPositions != null)")
        .addStatement("columnPositions.putAll(query.columns)")
        .endControlFlow()
        .addStatement("return query.queryDeep")
        .build();
  }

}
