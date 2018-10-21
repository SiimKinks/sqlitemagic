package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.util.Callback;
import com.siimkinks.sqlitemagic.util.ConditionCallback;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import java.util.List;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Builder;

import static com.siimkinks.sqlitemagic.WriterUtil.SQL_EXCEPTION;
import static com.siimkinks.sqlitemagic.WriterUtil.getDefaultValue;
import static com.siimkinks.sqlitemagic.WriterUtil.typeName;
import static com.siimkinks.sqlitemagic.util.ConditionCallback.ALWAYS_TRUE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_NEW_INSTANCE_WITH_ONLY_ID;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.ENTITY_VARIABLE;
import static com.siimkinks.sqlitemagic.writer.ModelWriter.MANAGER_VARIABLE;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RetrieveMethodsBodyBuilder {
  @Nullable
  private final TableElement tableElement;
  private final boolean forImmutable;
  private final TypeName tableElementTypeName;
  private final String tableName;
  private final boolean forBuilder;
  private final int allColumnsCount;
  private final ValueWriter valueWriter;

  private final CodeBlock.Builder fullObjectBuilder = CodeBlock.builder();
  private final CodeBlock.Builder fullObjectFromSelectionBuilder = CodeBlock.builder();
  private final CodeBlock.Builder fullObjectFromAllSelectionBuilder = CodeBlock.builder();
  private final CodeBlock.Builder shallowObjectBuilder = CodeBlock.builder();
  private final CodeBlock.Builder shallowObjectFromSelectionBuilder = CodeBlock.builder();
  private final CodeBlock.Builder shallowObjectFromAllSelectionBuilder = CodeBlock.builder();

  private final CodeBlock.Builder[] allBuilders = new CodeBlock.Builder[]{
      fullObjectBuilder,
      fullObjectFromSelectionBuilder,
      fullObjectFromAllSelectionBuilder,
      shallowObjectBuilder,
      shallowObjectFromSelectionBuilder,
      shallowObjectFromAllSelectionBuilder
  };

  private final CodeBlock.Builder[] shallowAllValuesRetrievingBuilders = new CodeBlock.Builder[]{
      shallowObjectBuilder,
      shallowObjectFromAllSelectionBuilder
  };

  private final CodeBlock.Builder[] allValuesRetrievingBuilders = new CodeBlock.Builder[]{
      fullObjectBuilder,
      fullObjectFromAllSelectionBuilder,
      shallowObjectBuilder,
      shallowObjectFromAllSelectionBuilder
  };

  private final CodeBlock.Builder[] fromSelectionRetrievingBuilders = new CodeBlock.Builder[]{
      fullObjectFromSelectionBuilder,
      shallowObjectFromSelectionBuilder
  };

  static final String[] complexColumnRetrieveParams = new String[]{"columnOffset"};

  public static RetrieveMethodsBodyBuilder create(EntityEnvironment entityEnvironment) {
    final TypeName tableElementTypeName = entityEnvironment.getTableElementTypeName();
    final TableElement tableElement = entityEnvironment.getTableElement();
    final RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder = builder()
        .tableElement(tableElement)
        .forImmutable(tableElement.isImmutable())
        .tableElementTypeName(tableElementTypeName)
        .tableName(tableElement.getTableName())
        .forBuilder(tableElement.hasBuilder())
        .allColumnsCount(tableElement.getAllColumnsCount())
        .valueWriter(tableElement.getValueWriter())
        .build();
    retrieveMethodsBodyBuilder.preBuild();
    return retrieveMethodsBodyBuilder;
  }

  public static RetrieveMethodsBodyBuilder create(ViewElement viewElement) {
    final TypeName tableElementTypeName = viewElement.getViewElementTypeName();
    final RetrieveMethodsBodyBuilder retrieveMethodsBodyBuilder = builder()
        .forImmutable(true)
        .tableElementTypeName(tableElementTypeName)
        .tableName(viewElement.getViewName())
        .forBuilder(viewElement.hasBuilder())
        .allColumnsCount(viewElement.getAllColumnsCount())
        .valueWriter(viewElement.getValueWriter())
        .build();
    retrieveMethodsBodyBuilder.preBuildForView();
    return retrieveMethodsBodyBuilder;
  }

  public CodeBlock getForFullObject() {
    return buildHeader()
        .add(fullObjectBuilder.build())
        .build();
  }

  public CodeBlock getForFullObjectFromSelection() {
    return buildHeaderForSelection()
        .add(fullObjectFromAllSelectionBuilder.build())
        .nextControlFlow("else")
        .add(fullObjectFromSelectionBuilder.build())
        .endControlFlow()
        .build();
  }

  public CodeBlock getForShallowObject() {
    return buildHeader()
        .add(shallowObjectBuilder.build())
        .build();
  }

  public CodeBlock getForShallowObjectFromSelection() {
    return buildHeaderForSelection()
        .add(shallowObjectFromAllSelectionBuilder.build())
        .nextControlFlow("else")
        .add(shallowObjectFromSelectionBuilder.build())
        .endControlFlow()
        .build();
  }

  public CodeBlock getForFullObjectForViewWithSelection() {
    return buildHeaderForViewWithSelection()
        .add(fullObjectFromSelectionBuilder.build())
        .build();
  }

  public CodeBlock getForShallowObjectForViewWithSelection() {
    return buildHeaderForViewWithSelection()
        .add(shallowObjectFromSelectionBuilder.build())
        .build();
  }

  private void preBuild() {
    if (forImmutable) {
      buildForImmutableObject();
    } else {
      buildForMutableObject();
    }
  }

  private void preBuildForView() {
    buildForView();
  }

  private CodeBlock.Builder buildHeader() {
    final CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("int thisTableOffset = columnOffset.value")
        .addStatement("columnOffset.value += $L", allColumnsCount);
    return builder;
  }

  private CodeBlock.Builder buildHeaderForSelection() {
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("String thisTableName = tableGraphNodeNames.get(nodeName)")
        .beginControlFlow("if (thisTableName == null)")
        .beginControlFlow("if (nodeName.length() > 0)")
        .addStatement("return null")
        .endControlFlow()
        .addStatement("thisTableName = $S", tableName)
        .endControlFlow()
        .addStatement("$T pos = columns.get(thisTableName)", TypeName.INT.box())
        .beginControlFlow("if (pos != null)")
        .addStatement("int thisTableOffset = pos");
    return builder;
  }

  private CodeBlock.Builder buildHeaderForViewWithSelection() {
    final CodeBlock.Builder builder = CodeBlock.builder();
    if (forBuilder) {
      builder.addStatement("$T pos", TypeName.INT.box());
    }
    return builder;
  }

  // -------------------------------------------
  //                  Immutable object methods
  // -------------------------------------------

  @Builder
  static class ImmutableObjectBuilderMetadata {
    final boolean fromSelection;
    final boolean allSelection;
    final boolean shallow;
    @lombok.NonNull
    final ConditionCallback<BaseColumnElement> isNeededCallback;
    @lombok.NonNull
    final String complexMethodName;
    final boolean respectOnlyUserProvidedColumnName; // default = false

    boolean fromSelectionOrAllSelection() {
      return fromSelection || allSelection;
    }

    static final ImmutableObjectBuilderMetadata DEFAULT = builder()
        .isNeededCallback(ALWAYS_TRUE)
        .complexMethodName(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .build();
  }

  private void buildForView() {
    final ValueWriter.Callback fullObjectValuesRetrieverFromSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .fromSelection(true)
        .isNeededCallback(ALWAYS_TRUE)
        .complexMethodName(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .respectOnlyUserProvidedColumnName(true)
        .build());
    fullObjectFromSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(fullObjectValuesRetrieverFromSelection));

    final ValueWriter.Callback shallowObjectValuesRetrieverFromSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .shallow(true)
        .fromSelection(true)
        .isNeededCallback(shallowQueryCondition)
        .complexMethodName(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .respectOnlyUserProvidedColumnName(true)
        .build());
    shallowObjectFromSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(shallowObjectValuesRetrieverFromSelection));
  }

  @SuppressWarnings("unchecked")
  private void buildForImmutableObject() {
    final ValueWriter valueWriter = this.valueWriter;

    final ValueWriter.Callback fullObjectValuesRetriever = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .isNeededCallback(ALWAYS_TRUE)
        .complexMethodName(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .build());
    fullObjectBuilder.add(valueWriter.buildAllValuesReturningSetter(fullObjectValuesRetriever));

    final ValueWriter.Callback fullObjectValuesRetrieverFromSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .fromSelection(true)
        .isNeededCallback(ALWAYS_TRUE)
        .complexMethodName(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .build());
    fullObjectFromSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(fullObjectValuesRetrieverFromSelection));

    final ValueWriter.Callback fullObjectValuesRetrieverFromAllSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .allSelection(true)
        .isNeededCallback(ALWAYS_TRUE)
        .complexMethodName(METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .build());
    fullObjectFromAllSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(fullObjectValuesRetrieverFromAllSelection));

    final ValueWriter.Callback shallowObjectValuesRetriever = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .shallow(true)
        .isNeededCallback(shallowQueryCondition)
        .complexMethodName(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .build());
    shallowObjectBuilder.add(valueWriter.buildAllValuesReturningSetter(shallowObjectValuesRetriever));

    final ValueWriter.Callback shallowObjectValuesRetrieverFromSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .shallow(true)
        .fromSelection(true)
        .isNeededCallback(shallowQueryCondition)
        .complexMethodName(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .build());
    shallowObjectFromSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(shallowObjectValuesRetrieverFromSelection));

    final ValueWriter.Callback shallowObjectValuesRetrieverFromAllSelection = immutableValuesRetriever(ImmutableObjectBuilderMetadata
        .builder()
        .shallow(true)
        .allSelection(true)
        .isNeededCallback(shallowQueryCondition)
        .complexMethodName(METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .build());
    shallowObjectFromAllSelectionBuilder.add(valueWriter.buildAllValuesReturningSetter(shallowObjectValuesRetrieverFromAllSelection));
  }

  ValueWriter.Callback immutableValuesRetriever(final ImmutableObjectBuilderMetadata metadata) {
    return new ValueWriter.Callback() {
      @Override
      public void call(CodeBlock.Builder mainBuilder, CodeBlock.Builder preCodeBuilder, BaseColumnElement columnElement, int pos, FormatData valueSetterFormat) {
        String offsetString;
        if (metadata.fromSelection) {
          if (forBuilder) {
            offsetString = "pos";
            mainBuilder.add(assignFromSelectionPositionGetterToPos(columnElement, metadata));
          } else {
            offsetString = columnElement.getElementName() + "Pos";
            preCodeBuilder.addStatement("final $T $L = $L",
                TypeName.INT.box(), offsetString, fromSelectionPositionGetter(columnElement, metadata));
            if (!columnElement.isReferencedColumn()) {
              if (columnElement.isNullable()) {
                valueSetterFormat = FormatData.create(String.format(offsetString + " == null || cursor.isNull(" + offsetString + ") ? null : %s",
                    valueSetterFormat.getFormat()), valueSetterFormat.getArgs());
              } else if (columnElement.hasNullableAnnotation()) {
                final String defaultValue = getDefaultValue(typeName(columnElement.getDeserializedType()));
                valueSetterFormat = FormatData.create(String.format(offsetString + " == null ? %s : %s",
                    defaultValue, valueSetterFormat.getFormat()), valueSetterFormat.getArgs());
              }
            }
          }
        } else {
          if (forBuilder) {
            offsetString = "thisTableOffset";
            if (columnElement.isNullable()) {
              mainBuilder.beginControlFlow("if (!cursor.isNull($L))", offsetString);
            }
          } else {
            offsetString = getOffsetString(pos);
            if (columnElement.isNullable() && !columnElement.isReferencedColumn()) {
              valueSetterFormat = FormatData.create(String.format("cursor.isNull(" + offsetString + ") ? null : %s",
                  valueSetterFormat.getFormat()), valueSetterFormat.getArgs());
            }
          }
        }

        addImmutableValueRetriever(mainBuilder, preCodeBuilder, columnElement, valueSetterFormat, offsetString, metadata);

        if (metadata.fromSelection) {
        } else {
          if (forBuilder) {
            if (columnElement.isNullable()) {
              mainBuilder.endControlFlow();
            }
            if (pos < allColumnsCount - 1) {
              mainBuilder.addStatement(offsetString + "++");
            }
          }
        }
      }
    };
  }

  private void addImmutableValueRetriever(final CodeBlock.Builder builder, final CodeBlock.Builder preCodeBuilder,
                                          final BaseColumnElement columnElement, final FormatData valueSetterFormat,
                                          final String offsetString, final ImmutableObjectBuilderMetadata metadata) {
    final String settableValue = columnElement.cursorGetter("cursor", offsetString);
    if (columnElement.isReferencedColumn()) {
      addImmutableValueReferencedColumnRetrieve(builder, preCodeBuilder, columnElement, valueSetterFormat, offsetString, metadata, settableValue);
    } else {
      addSimpleSelectionCheck(builder, preCodeBuilder, tableName, columnElement, offsetString, forBuilder, metadata.fromSelection);
      addRetrieveClause(builder, valueSetterFormat, settableValue);
      addSimpleSelectionCheckEnd(builder, columnElement, forBuilder, metadata.fromSelection);
    }
  }

  private void addImmutableValueReferencedColumnRetrieve(CodeBlock.Builder builder, CodeBlock.Builder preCodeBuilder, BaseColumnElement columnElement, FormatData valueSetterFormat, String offsetString, ImmutableObjectBuilderMetadata metadata, String settableValue) {
    if (columnElement.isHandledRecursively() && metadata.isNeededCallback.call(columnElement)) {
      final String[] complexCallArgs = metadata.fromSelectionOrAllSelection() ? complexColumnRetrieveFromSelectionParams(columnElement, metadata) : complexColumnRetrieveParams;
      if (forBuilder) {
        addImmutableValueRecursiveColumnRetrieveForBuilder(builder, columnElement, valueSetterFormat, offsetString, metadata, complexCallArgs);
      } else {
        addImmutableValueRecursiveColumnRetrieveForCreator(builder, preCodeBuilder, columnElement, valueSetterFormat, offsetString, metadata, complexCallArgs);
      }
    } else {
      addImmutableValueNonRecursiveColumnRetrieve(builder, preCodeBuilder, tableName, columnElement, valueSetterFormat,
          offsetString, forBuilder, settableValue, metadata.fromSelection);
    }
  }

  private void addImmutableValueRecursiveColumnRetrieveForBuilder(CodeBlock.Builder builder, BaseColumnElement columnElement, FormatData valueSetterFormat, String offsetString, ImmutableObjectBuilderMetadata metadata, String[] complexCallArgs) {
    final FormatData complexCall = callToComplexColumnRetrieve(metadata.complexMethodName, columnElement, complexCallArgs);
    final boolean columnElementNullable = columnElement.isNullable();
    if (metadata.fromSelection) {
      builder.beginControlFlow("if ($L != null)", offsetString);
      if (columnElementNullable) {
        builder.beginControlFlow("if (!cursor.isNull($L))", offsetString);
      }
      final TableElement referencedTable = columnElement.getReferencedTable();
      final String tmpVariableName = columnElement.getElementName();
      builder.addStatement(complexCall.formatInto("final $T $L = %s"), complexCall
          .getWithOtherArgsBefore(
              referencedTable.getTableElementTypeName(),
              tmpVariableName))
          .beginControlFlow("if ($L != null)", tmpVariableName);
      addRetrieveClause(builder, valueSetterFormat, tmpVariableName);
      builder.nextControlFlow("else");
      if (!referencedTable.canBeInstantiatedWithOnlyId()) {
        builder.addStatement("throw new $T(\"Complex column $L cannot be instantiated with only id\")", SQL_EXCEPTION, columnElement.getElementName());
      } else {
        final String settableValue = referencedTable.getIdColumn().cursorGetter("cursor", offsetString);
        final FormatData instanceWithOnlyIdCall = complexColumnNewInstanceWithOnlyIdCall(settableValue, referencedTable);
        addRetrieveClause(builder, valueSetterFormat, instanceWithOnlyIdCall.getFormat(), instanceWithOnlyIdCall.getArgs());
      }
      builder.endControlFlow();
      if (columnElementNullable) {
        builder.endControlFlow();
      }
      if (!metadata.respectOnlyUserProvidedColumnName) {
        addSelectAllFromTableNextControlFlowCheck(builder, columnElement);
        addRetrieveClause(builder, valueSetterFormat, complexCall.getFormat(), complexCall.getArgs());
      }
      if (!columnElementNullable) {
        builder.nextControlFlow("else");
        addColumnMissingFromSelectionExceptionStatement(builder, columnElement, tableName);
      }
      builder.endControlFlow();
    } else {
      addRetrieveClause(builder, valueSetterFormat, complexCall.getFormat(), complexCall.getArgs());
      if (columnElementNullable && !metadata.allSelection) {
        builder.nextControlFlow("else")
            .add(columnOffsetCorrectionStatement(columnElement, metadata.complexMethodName));
      }
    }
  }

  private void addImmutableValueRecursiveColumnRetrieveForCreator(CodeBlock.Builder builder, CodeBlock.Builder preCodeBuilder, BaseColumnElement columnElement, FormatData valueSetterFormat, String offsetString, ImmutableObjectBuilderMetadata metadata, String[] complexCallArgs) {
    final String columnElementName = columnElement.getElementName();
    final TypeName referencedTableTypeName = columnElement.getReferencedTable().getTableElementTypeName();
    final FormatData complexCall = callToComplexColumnRetrieve(metadata.complexMethodName, columnElement, complexCallArgs);
    final boolean columnElementNullable = columnElement.isNullable();
    if (metadata.fromSelection) {
      final TableElement referencedTable = columnElement.getReferencedTable();
      preCodeBuilder.addStatement("$T $L$L", referencedTableTypeName, columnElementName, columnElementNullable ? " = null" : "")
          .beginControlFlow("if ($L != null)", offsetString);
      if (columnElementNullable) {
        preCodeBuilder.beginControlFlow("if (!cursor.isNull($L))", offsetString);
      }
      preCodeBuilder.addStatement(String.format("$L = %s", complexCall.getFormat()),
          complexCall.getWithOtherArgsBefore(columnElementName));
      preCodeBuilder.beginControlFlow("if ($L == null)", columnElementName);
      if (!referencedTable.canBeInstantiatedWithOnlyId()) {
        preCodeBuilder.addStatement("throw new $T(\"Complex column $L cannot be instantiated with only id\")", SQL_EXCEPTION, columnElementName);
      } else {
        final String settableValue = referencedTable.getIdColumn().cursorGetter("cursor", offsetString);
        final FormatData instanceWithOnlyIdCall = complexColumnNewInstanceWithOnlyIdCall(settableValue, referencedTable);
        preCodeBuilder.addStatement(String.format("$L = %s", instanceWithOnlyIdCall.getFormat()),
            instanceWithOnlyIdCall.getWithOtherArgsBefore(columnElementName));
      }
      preCodeBuilder.endControlFlow();
      if (columnElementNullable) {
        preCodeBuilder.endControlFlow();
      }
      if (!metadata.respectOnlyUserProvidedColumnName) {
        addSelectAllFromTableNextControlFlowCheck(preCodeBuilder, columnElement);
        preCodeBuilder.addStatement(String.format("$L = %s", complexCall.getFormat()),
            complexCall.getWithOtherArgsBefore(columnElementName));
      }
      if (!columnElementNullable) {
        preCodeBuilder.nextControlFlow("else");
        addColumnMissingFromSelectionExceptionStatement(preCodeBuilder, columnElement, tableName);
      }
      preCodeBuilder.endControlFlow();
      addRetrieveClause(builder, valueSetterFormat, columnElementName);
    } else {
      addImmutableValueRecursiveColumnRetrieveForCreatorWithoutSelection(builder, preCodeBuilder,
          columnElement, valueSetterFormat, offsetString, complexCall,
          metadata.complexMethodName, metadata.allSelection);
    }
  }

  static void addImmutableValueRecursiveColumnRetrieveForCreatorWithoutSelection(CodeBlock.Builder builder, CodeBlock.Builder preCodeBuilder, BaseColumnElement columnElement, FormatData valueSetterFormat, String offsetString, FormatData complexCall, String complexMethodName, boolean allSelection) {
    final String columnElementName = columnElement.getElementName();
    final TypeName referencedTableTypeName = columnElement.getReferencedTable().getTableElementTypeName();
    final boolean columnElementNullable = columnElement.isNullable();
    if (columnElementNullable) {
      preCodeBuilder.addStatement("$T $L = null", referencedTableTypeName, columnElementName)
          .beginControlFlow("if (!cursor.isNull($L))", offsetString)
          .addStatement(String.format("$L = %s", complexCall.getFormat()),
              complexCall.getWithOtherArgsBefore(columnElementName));
      if (!allSelection) {
        preCodeBuilder.nextControlFlow("else")
            .add(columnOffsetCorrectionStatement(columnElement, complexMethodName));
      }
      preCodeBuilder.endControlFlow();
      addRetrieveClause(builder, valueSetterFormat, columnElementName);
    } else {
      if (allSelection) {
        addRetrieveClause(builder, valueSetterFormat, complexCall.getFormat(), complexCall.getArgs());
      } else {
        preCodeBuilder.addStatement(String.format("final $T $L = %s", complexCall.getFormat()),
            complexCall.getWithOtherArgsBefore(referencedTableTypeName, columnElementName));
        addRetrieveClause(builder, valueSetterFormat, columnElementName);
      }
    }
  }

  static void addImmutableValueNonRecursiveColumnRetrieve(CodeBlock.Builder builder, CodeBlock.Builder preCodeBuilder,
                                                          String parentTableName,
                                                          BaseColumnElement columnElement, FormatData valueSetterFormat,
                                                          String offsetString, boolean forBuilder,
                                                          String settableValue,
                                                          boolean fromSelection) {
    final String columnElementName = columnElement.getElementName();
    final TableElement referencedTable = columnElement.getReferencedTable();
    final FormatData complexColumnNewInstanceCall = complexColumnNewInstanceWithOnlyIdCall(settableValue, referencedTable);
    addSimpleSelectionCheck(builder, preCodeBuilder, parentTableName, columnElement, offsetString, forBuilder, fromSelection);
    if (forBuilder || !columnElement.isNullable()) {
      addRetrieveClause(builder, valueSetterFormat, complexColumnNewInstanceCall.getFormat(), complexColumnNewInstanceCall.getArgs());
    } else {
      preCodeBuilder.addStatement("$T $L = null", referencedTable.getTableElementTypeName(), columnElementName);
      if (fromSelection) {
        preCodeBuilder.beginControlFlow("if ($1L != null && !cursor.isNull($1L))", offsetString);
      } else {
        preCodeBuilder.beginControlFlow("if (!cursor.isNull($L))", offsetString);
      }
      preCodeBuilder.addStatement(complexColumnNewInstanceCall.formatInto("$L = %s"),
          complexColumnNewInstanceCall.getWithOtherArgsBefore(columnElementName))
          .endControlFlow();
      addRetrieveClause(builder, valueSetterFormat, columnElementName);
    }
    addSimpleSelectionCheckEnd(builder, columnElement, forBuilder, fromSelection);
  }

  static void addSimpleSelectionCheck(CodeBlock.Builder builder, CodeBlock.Builder preCodeBuilder,
                                      String parentTableName,
                                      BaseColumnElement columnElement,
                                      String offsetString, boolean forBuilder, boolean fromSelection) {
    if (fromSelection) {
      if (forBuilder) {
        if (columnElement.isNullable()) {
          builder.beginControlFlow("if ($1L != null && !cursor.isNull($1L))", offsetString);
        } else if (columnElement.hasNullableAnnotation()) {
          builder.beginControlFlow("if ($1L != null)", offsetString);
        } else {
          addColumnMissingFromSelectionException(builder, columnElement, offsetString, parentTableName);
        }
      } else {
        if (!columnElement.isNullable()) {
          addColumnMissingFromSelectionException(preCodeBuilder, columnElement, offsetString, parentTableName);
        }
      }
    }
  }

  static void addSimpleSelectionCheckEnd(CodeBlock.Builder builder, BaseColumnElement columnElement, boolean forBuilder, boolean fromSelection) {
    if (fromSelection && forBuilder && (columnElement.isNullable() || columnElement.hasNullableAnnotation())) {
      builder.endControlFlow();
    }
  }

  static void addRetrieveClause(CodeBlock.Builder builder, FormatData valueSetterFormat, String settableValue, Object... args) {
    builder.add(String.format(valueSetterFormat.getFormat(), settableValue), valueSetterFormat.getWithOtherArgsAfter(args));
  }

  @NonNull
  static FormatData complexColumnNewInstanceWithOnlyIdCall(String settableValue, TableElement referencedTable) {
    return FormatData.create("$T.$L($L)",
        EntityEnvironment.getGeneratedDaoClassName(referencedTable),
        METHOD_NEW_INSTANCE_WITH_ONLY_ID,
        settableValue);
  }

  static void addColumnMissingFromSelectionException(CodeBlock.Builder builder, BaseColumnElement columnElement, String offsetString, String parentTableName) {
    if (!columnElement.hasNullableAnnotation()) {
      builder.beginControlFlow("if ($L == null)", offsetString);
      addColumnMissingFromSelectionExceptionStatement(builder, columnElement, parentTableName);
      builder.endControlFlow();
    }
  }

  static void addColumnMissingFromSelectionExceptionStatement(CodeBlock.Builder builder, BaseColumnElement columnElement, String parentTableName) {
    builder.addStatement("throw new $T($S)", SQL_EXCEPTION, "Selected columns did not contain table \"" + parentTableName +
        "\" required column \"" + columnElement.getColumnName() + "\"");
  }

  // -------------------------------------------
  //           Mutable object methods
  // -------------------------------------------

  private void buildForMutableObject() {
    if (tableElement == null) {
      throw new IllegalStateException("Cannot build for mutable object when table element is missing");
    }
    forAllBuilders(addMutableTableNewInstanceVariable());
    final List<ColumnElement> allColumns = tableElement.getAllColumns();
    for (int i = 0, columnsCount = allColumns.size(); i < columnsCount; i++) {
      final ColumnElement columnElement = allColumns.get(i);
      buildForAllValuesRetrievingMutableObject(columnElement, i, columnsCount);
      buildForSelectionValuesRetrievingMutableObject(columnElement);
    }
    forAllBuilders(new Callback<CodeBlock.Builder>() {
      @Override
      public void call(CodeBlock.Builder builder) {
        builder.addStatement("return $L", ENTITY_VARIABLE);
      }
    });
  }

  private void buildForAllValuesRetrievingMutableObject(final ColumnElement columnElement, int pos, int columnsCount) {
    CodeBlock.Builder commonCodeBuilder = CodeBlock.builder();
    final String offsetString = "thisTableOffset";
    if (columnElement.isNullable()) {
      commonCodeBuilder.beginControlFlow("if (!cursor.isNull($L))", offsetString);
    }

    if (columnElement.isReferencedColumn()) {
      if (columnElement.isHandledRecursively()) {
        commonCodeBuilder = flushCommonCodeTo(allValuesRetrievingBuilders, commonCodeBuilder);
        // shallow
        if (columnElement.isNeededForShallowQuery()) {
          addMutableComplexColumnRetrieve(shallowObjectBuilder, shallowObjectFromAllSelectionBuilder, METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION, columnElement);
        } else {
          forEachBuilder(shallowAllValuesRetrievingBuilders, new Callback<CodeBlock.Builder>() {
            @Override
            public void call(CodeBlock.Builder builder) {
              addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(builder, columnElement, offsetString);
            }
          });
        }
        // deep
        addMutableComplexColumnRetrieve(fullObjectBuilder, fullObjectFromAllSelectionBuilder, METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, columnElement);
      } else {
        addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(commonCodeBuilder, columnElement, offsetString);
      }
    } else {
      addMutableTableSimpleColumnSetter(commonCodeBuilder, columnElement, offsetString);
    }

    if (columnElement.isNullable()) {
      commonCodeBuilder.endControlFlow();
    }
    if (pos < columnsCount - 1) {
      commonCodeBuilder.addStatement(offsetString + "++");
    }
    addCommonCodeTo(allValuesRetrievingBuilders, commonCodeBuilder);
  }

  private void buildForSelectionValuesRetrievingMutableObject(ColumnElement columnElement) {
    final String offsetString = "pos";
    CodeBlock.Builder commonCodeBuilder = CodeBlock.builder()
        .add(assignFromSelectionPositionGetterToPos(columnElement, ImmutableObjectBuilderMetadata.DEFAULT));
    addFromSelectionAddCheck(commonCodeBuilder, columnElement);
    if (columnElement.isReferencedColumn()) {
      if (columnElement.isHandledRecursively()) {
        commonCodeBuilder.beginControlFlow("if (!cursor.isNull(pos))");
        commonCodeBuilder = flushCommonCodeTo(fromSelectionRetrievingBuilders, commonCodeBuilder);
        final String[] complexCallParams = complexColumnRetrieveFromSelectionParams(columnElement, ImmutableObjectBuilderMetadata.DEFAULT);
        // shallow
        if (columnElement.isNeededForShallowQuery()) {
          addMutableTableComplexColumnRetrieveFromSelection(shallowObjectFromSelectionBuilder, METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
              offsetString, columnElement, complexCallParams);
        } else {
          addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(shallowObjectFromSelectionBuilder, columnElement, offsetString);
          shallowObjectFromSelectionBuilder.endControlFlow();
        }
        // deep
        addMutableTableComplexColumnRetrieveFromSelection(fullObjectFromSelectionBuilder, METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
            offsetString, columnElement, complexCallParams);
      } else {
        addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(commonCodeBuilder, columnElement, offsetString);
      }
    } else {
      addMutableTableSimpleColumnSetter(commonCodeBuilder, columnElement, offsetString);
    }
    commonCodeBuilder.endControlFlow();
    addCommonCodeTo(fromSelectionRetrievingBuilders, commonCodeBuilder);
  }

  private CodeBlock assignFromSelectionPositionGetterToPos(BaseColumnElement columnElement, ImmutableObjectBuilderMetadata metadata) {
    return CodeBlock.builder()
        .addStatement("pos = $L", fromSelectionPositionGetter(columnElement, metadata))
        .build();
  }

  private String fromSelectionPositionGetter(BaseColumnElement columnElement, ImmutableObjectBuilderMetadata metadata) {
    if (metadata.respectOnlyUserProvidedColumnName) {
      return "columns.get(\"" + columnElement.getColumnName() + "\")";
    }
    return "columns.get(thisTableName + \"." + columnElement.getColumnName() + "\")";
  }

  private void addFromSelectionAddCheck(CodeBlock.Builder builder, ColumnElement columnElement) {
    if (columnElement.isHandledRecursively()) {
      builder.beginControlFlow("if (pos != null)");
    } else {
      builder.beginControlFlow("if (pos != null && !cursor.isNull(pos))");
    }
  }

  private void addMutableComplexColumnRetrieve(CodeBlock.Builder builder, CodeBlock.Builder fromAllSelectionBuilder,
                                               String complexMethodName, ColumnElement columnElement) {
    addMutableTableComplexColumnRetrieve(builder, complexMethodName, columnElement, complexColumnRetrieveParams);
    if (columnElement.isNullable()) {
      builder.nextControlFlow("else")
          .add(columnOffsetCorrectionStatement(columnElement, complexMethodName));
    }

    addMutableTableComplexColumnRetrieve(fromAllSelectionBuilder, complexMethodName, columnElement,
        complexColumnRetrieveFromSelectionParams(columnElement, ImmutableObjectBuilderMetadata.DEFAULT));
  }

  private void addMutableTableComplexColumnRetrieve(CodeBlock.Builder builder, String complexMethodName, ColumnElement columnElement, String... args) {
    final FormatData settableValue = callToComplexColumnRetrieve(complexMethodName, columnElement, args);
    final FormatData deserializedValueSetter = columnElement.deserializedValueSetter(ENTITY_VARIABLE, settableValue.getFormat(), MANAGER_VARIABLE);
    builder.addStatement(deserializedValueSetter.getFormat(),
        deserializedValueSetter.getWithOtherArgsBefore(settableValue.getArgs()));
  }

  private void addMutableTableComplexColumnRetrieveFromSelection(CodeBlock.Builder builder, String complexMethodName, String offsetString, ColumnElement columnElement, String... args) {
    final FormatData settableValue = callToComplexColumnRetrieve(complexMethodName, columnElement, args);
    final String tmpVariableName = columnElement.getElementName();
    final TableElement referencedTable = columnElement.getReferencedTable();
    builder.addStatement(settableValue.formatInto("final $T $L = %s"), settableValue
        .getWithOtherArgsBefore(
            referencedTable.getTableElementTypeName(),
            tmpVariableName))
        .beginControlFlow("if ($L != null)", tmpVariableName);
    FormatData deserializedValueSetter = columnElement.deserializedValueSetter(ENTITY_VARIABLE, tmpVariableName, MANAGER_VARIABLE);
    builder.addStatement(deserializedValueSetter.getFormat(), deserializedValueSetter.getArgs())
        .nextControlFlow("else");
    if (!referencedTable.canBeInstantiatedWithOnlyId()) {
      builder.addStatement("throw new $T(\"Complex column $L cannot be instantiated with only id\")", SQL_EXCEPTION, columnElement.getElementName());
    } else {
      addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(builder, columnElement, offsetString);
    }
    builder.endControlFlow();

    builder.endControlFlow(); // ends outer if
    addSelectAllFromTableNextControlFlowCheck(builder, columnElement);
    addMutableTableComplexColumnRetrieve(builder, complexMethodName, columnElement, args);

  }

  private void addSelectAllFromTableNextControlFlowCheck(CodeBlock.Builder builder, BaseColumnElement columnElement) {
    builder.nextControlFlow("else if (columns.get(tableGraphNodeNames.get(nodeName + $S)) != null)",
        columnElement.getColumnName());
  }

  @NonNull
  static FormatData callToComplexColumnRetrieve(String methodName, BaseColumnElement columnElement, String... args) {
    final StringBuilder settableValue = new StringBuilder("$T.$L(cursor");
    for (String arg : args) {
      settableValue.append(", ")
          .append(arg);
    }
    settableValue.append(")");
    final TableElement referencedTable = columnElement.getReferencedTable();
    return FormatData.create(settableValue.toString(),
        EntityEnvironment.getGeneratedDaoClassName(referencedTable),
        referencedTable.hasAnyPersistedComplexColumns() ? methodName : METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION);
  }

  private void addMutableTableComplexColumnWithOnlyIdForRetrieveFromPosition(CodeBlock.Builder builder, ColumnElement columnElement, String offsetString) {
    final TableElement referencedTable = columnElement.getReferencedTable();
    final String settableValue = columnElement.cursorGetter("cursor", offsetString);
    final FormatData complexColumnNewInstanceCall = complexColumnNewInstanceWithOnlyIdCall(settableValue, referencedTable);
    final FormatData deserializedValueSetter = columnElement.deserializedValueSetter(
        ENTITY_VARIABLE,
        complexColumnNewInstanceCall.getFormat(),
        MANAGER_VARIABLE
    );
    builder.addStatement(deserializedValueSetter.getFormat(),
        deserializedValueSetter.getWithOtherArgsAfter(complexColumnNewInstanceCall.getArgs()));
  }

  private void addMutableTableSimpleColumnSetter(CodeBlock.Builder builder, ColumnElement columnElement, String offsetString) {
    final FormatData deserializedValueSetter = columnElement.deserializedValueSetter(
        ENTITY_VARIABLE,
        columnElement.cursorGetter("cursor", offsetString),
        MANAGER_VARIABLE);
    builder.addStatement(deserializedValueSetter.getFormat(), deserializedValueSetter.getArgs());
  }

  private Callback<CodeBlock.Builder> addMutableTableNewInstanceVariable() {
    return new Callback<CodeBlock.Builder>() {
      @Override
      public void call(CodeBlock.Builder builder) {
        builder.addStatement("final $1T $2L = new $1T()", tableElementTypeName, ENTITY_VARIABLE);
      }
    };
  }

  private void addCommonCodeTo(CodeBlock.Builder[] builders, CodeBlock.Builder commonCodeBuilder) {
    final CodeBlock commonCode = commonCodeBuilder.build();
    for (CodeBlock.Builder builder : builders) {
      builder.add(commonCode);
    }
  }

  @CheckResult
  private CodeBlock.Builder flushCommonCodeTo(CodeBlock.Builder[] builders, CodeBlock.Builder commonCodeBuilder) {
    addCommonCodeTo(builders, commonCodeBuilder);
    return CodeBlock.builder();
  }

  private void forAllBuilders(Callback<CodeBlock.Builder> callback) {
    for (CodeBlock.Builder builder : allBuilders) {
      callback.call(builder);
    }
  }

  private void forEachBuilder(CodeBlock.Builder[] builders, Callback<CodeBlock.Builder> callback) {
    for (CodeBlock.Builder builder : builders) {
      callback.call(builder);
    }
  }

  static CodeBlock columnOffsetCorrectionStatement(BaseColumnElement columnElement, String complexMethodName) {
    final TableElement referencedTable = columnElement.getReferencedTable();
    return CodeBlock.builder()
        .addStatement("columnOffset.value += $L",
            complexMethodName.startsWith("shallow") ? referencedTable.getGraphMinimalColumnsCount() : referencedTable.getGraphAllColumnsCount())
        .build();
  }

  @NonNull
  private String[] complexColumnRetrieveFromSelectionParams(BaseColumnElement columnElement, ImmutableObjectBuilderMetadata metadata) {
    if (metadata.respectOnlyUserProvidedColumnName) {
      return new String[]{"columns", "tableGraphNodeNames", "nodeName"};
    }
    return new String[]{"columns", "tableGraphNodeNames", ("nodeName + \"" + columnElement.getColumnName() + "\"")};
  }

  @NonNull
  private final ConditionCallback<BaseColumnElement> shallowQueryCondition = new ConditionCallback<BaseColumnElement>() {
    @Override
    public boolean call(BaseColumnElement columnElement) {
      return columnElement.isNeededForShallowQuery();
    }
  };

  @NonNull
  static String getOffsetString(int i) {
    if (i == 0) {
      return "thisTableOffset";
    }
    return "thisTableOffset + " + i;
  }
}
