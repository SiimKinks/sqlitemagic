package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.NameConst;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import lombok.Getter;

import static com.siimkinks.sqlitemagic.Const.STATIC_METHOD_MODIFIERS;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_GET_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SET_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.StructureWriter.columnFieldName;
import static com.siimkinks.sqlitemagic.writer.StructureWriter.structureFieldName;

public class EntityEnvironment {

  @Getter
  private final TableElement tableElement;
  @Getter
  private final TypeName tableElementTypeName;
  @Getter
  private final String daoClassNameString;
  @Getter
  private final String handlerClassNameString;
  @Getter
  private final ClassName daoClassName;
  @Getter
  private final ClassName handlerClassName;
  private MethodSpec entityIdSetter;
  private MethodSpec entityIdGetter;

  public EntityEnvironment(TableElement tableElement, TypeName tableElementTypeName) {
    this.tableElement = tableElement;
    this.tableElementTypeName = tableElementTypeName;
    this.daoClassNameString = getGeneratedDaoClassNameString(tableElement.getTableElement());
    this.handlerClassNameString = getGeneratedHandlerClassNameString(tableElement);
    this.daoClassName = getGeneratedDaoClassName(tableElement);
    this.handlerClassName = getGeneratedHandlerClassName(tableElement);
  }

  public MethodSpec getEntityIdSetter() {
    if (entityIdSetter == null) {
      entityIdSetter = entityIdSetter(tableElement, tableElementTypeName);
    }
    return entityIdSetter;
  }

  private MethodSpec entityIdSetter(TableElement tableElement, TypeName tableElementTypeName) {
    final boolean isImmutable = tableElement.isImmutable();
    final ColumnElement idColumn = tableElement.getIdColumn();
    final String idSetter = idColumn.valueSetter(ENTITY_VARIABLE, "id");
    return WriterUtil.createMagicInvokableMethod(daoClassNameString, METHOD_SET_ID)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addParameter(idColumn.getDeserializedTypeName(), "id")
        .addStatement(isImmutable ? "return $L" : "$L", idSetter)
        .returns(isImmutable ? tableElementTypeName : TypeName.VOID)
        .build();
  }


  public MethodSpec getEntityIdGetter() {
    if (entityIdGetter == null) {
      entityIdGetter = entityIdGetter(tableElement, tableElementTypeName);
    }
    return entityIdGetter;
  }

  private static MethodSpec entityIdGetter(TableElement tableElement, TypeName tableElementTypeName) {
    final ColumnElement idColumn = tableElement.getIdColumn();
    final String idGetter = idColumn.valueGetter(ENTITY_VARIABLE);
    return MethodSpec.methodBuilder(METHOD_GET_ID)
        .addModifiers(STATIC_METHOD_MODIFIERS)
        .addParameter(tableElementTypeName, ENTITY_VARIABLE)
        .addStatement("return $L", idGetter)
        .returns(idColumn.getDeserializedTypeName())
        .build();
  }

  public void addInlineIdVariable(CodeBlock.Builder builder) {
    builder.add("$L.$N($L)",
        daoClassNameString,
        getEntityIdGetter(),
        ENTITY_VARIABLE);
  }

  public CodeBlock returnIdFromEntity() {
    final CodeBlock.Builder builder = CodeBlock.builder();
    builder.add("return ");
    addInlineIdVariable(builder);
    builder.add(";\n");
    return builder.build();
  }

  public CodeBlock getFinalIdVariable() {
    return getFinalIdVariable("id");
  }

  public CodeBlock getFinalIdVariable(String idVariableName) {
    return CodeBlock.builder()
        .addStatement("final $T $L = $L.$N($L)",
            tableElement.getIdColumn().getDeserializedTypeName(),
            idVariableName,
            daoClassNameString,
            getEntityIdGetter(),
            ENTITY_VARIABLE)
        .build();
  }

  public CodeBlock getIdVariable() {
    return CodeBlock.builder()
        .addStatement("$T id = $L.$N($L)",
            tableElement.getIdColumn().getDeserializedTypeName(),
            daoClassNameString,
            getEntityIdGetter(),
            ENTITY_VARIABLE)
        .build();
  }

  public CodeBlock setIdToVariable(String variableName) {
    return CodeBlock.builder()
        .addStatement("$L = $L.$N($L)",
            variableName,
            daoClassNameString,
            getEntityIdGetter(),
            ENTITY_VARIABLE)
        .build();
  }

  public FormatData getWhereIdStatementPart() {
    return FormatData.create("\"$L=?\", new $T{Long.toString($L.$N($L))}",
        tableElement.getIdColumn().getColumnName(),
        String[].class,
        daoClassNameString,
        getEntityIdGetter(),
        ENTITY_VARIABLE);
  }

  public static FormatData idGetterFromDao(TableElement tableElement, String entityVariable) {
    return FormatData.create("$T.$L($L)",
        getGeneratedDaoClassName(tableElement),
        METHOD_GET_ID,
        entityVariable);
  }

  public static FormatData idGetterFromDaoIfNeeded(ColumnElement columnElement, String entityVariable) {
    final TableElement referencedTable = columnElement.getReferencedTable();
    final ColumnElement idColumn = referencedTable.getIdColumn();
    if (idColumn.hasModifier(Modifier.PUBLIC)) {
      return idColumn.serializedValueGetterFromEntity(entityVariable);
    }
    return idGetterFromDao(referencedTable, entityVariable);
  }

  public FormatData getWhereIdStatementPartWithProvidedIdVariable(String variableName) {
    return FormatData.create("\"$L=?\", new $T{Long.toString($L)}",
        tableElement.getIdColumn().getColumnName(),
        String[].class,
        variableName);
  }

  public static String getCreateInvokeKey(String callClassName, String tableClassName) {
    return getInvokeKey(callClassName, tableClassName, "create");
  }

  public static String getInvokeKey(String callClassName, String tableClassName, String callMethodName) {
    return String.format(PACKAGE_ROOT + ".%s.%s#%s", getGeneratedHandlerClassNameString(tableClassName), callClassName, callMethodName);
  }

  public static FormatData tableStructureIdConstant(TableElement tableElement) {
    return FormatData.create("$T.$L.$L",
        getGeneratedTableStructureInterfaceName(tableElement),
        structureFieldName(tableElement),
        columnFieldName(tableElement.getIdColumn()));
  }

  public static FormatData tableStructureConstant(TableElement tableElement) {
    return FormatData.create("$T.$L", getGeneratedTableStructureInterfaceName(tableElement), structureFieldName(tableElement));
  }

  public static FormatData tableNameFromStructureConstant(TableElement tableElement) {
    return FormatData.create("$T.$L.name", getGeneratedTableStructureInterfaceName(tableElement), structureFieldName(tableElement));
  }

  public static String getGeneratedDaoClassNameString(TypeElement element) {
    return String.format("SqliteMagic_%s_%s", element.getSimpleName().toString(), NameConst.CLASS_MODEL_DAO);
  }

  public static String getGeneratedHandlerClassNameString(TableElement tableElement) {
    return getGeneratedHandlerClassNameString(tableElement.getTableElementName());
  }

  public static String getGeneratedHandlerClassNameString(ViewElement viewElement) {
    return getGeneratedHandlerClassNameString(viewElement.getViewElementName());
  }

  public static String getGeneratedHandlerClassNameString(String baseClassName) {
    return String.format("SqliteMagic_%s_%s", baseClassName, NameConst.CLASS_MODEL_HANDLER);
  }

  public static ClassName getGeneratedTableStructureInterfaceName(TableElement tableElement) {
    return ClassName.get(PACKAGE_ROOT, getGeneratedTableStructureInterfaceNameString(tableElement));
  }

  public static String getGeneratedTableStructureInterfaceNameString(TableElement tableElement) {
    return getGeneratedTableStructureInterfaceNameString(tableElement.getTableElementName());
  }

  public static String getGeneratedTableStructureInterfaceNameString(String baseClassName) {
    return String.format("%s%s", baseClassName, NameConst.CLASS_TABLE_STRUCTURE);
  }

  public static String getGeneratedHandlerInnerClassName(TableElement tableElement, String innerClassName) {
    return getGeneratedHandlerClassNameString(tableElement) + "." + innerClassName;
  }

  public static ClassName getGeneratedDaoClassName(TableElement tableElement) {
    return ClassName.get(tableElement.getPackageName(), getGeneratedDaoClassNameString(tableElement.getTableElement()));
  }

  public static ClassName getGeneratedDaoClassName(ViewElement viewElement) {
    return ClassName.get(viewElement.getPackageName(), getGeneratedDaoClassNameString(viewElement.getViewElement()));
  }

  public static ClassName getGeneratedHandlerClassName(TableElement tableElement) {
    return ClassName.get(PACKAGE_ROOT, getGeneratedHandlerClassNameString(tableElement));
  }

  public static ClassName getGeneratedHandlerClassName(ViewElement viewElement) {
    return ClassName.get(PACKAGE_ROOT, getGeneratedHandlerClassNameString(viewElement));
  }
}
