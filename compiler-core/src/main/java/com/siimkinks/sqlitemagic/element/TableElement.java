package com.siimkinks.sqlitemagic.element;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.exception.DuplicateException;
import com.siimkinks.sqlitemagic.exception.DuplicateIdException;
import com.siimkinks.sqlitemagic.exception.DuplicateNameException;
import com.siimkinks.sqlitemagic.util.FormatData;
import com.siimkinks.sqlitemagic.util.StringUtil;
import com.siimkinks.sqlitemagic.writer.DataClassWriter;
import com.siimkinks.sqlitemagic.writer.EntityEnvironment;
import com.siimkinks.sqlitemagic.writer.ValueBuilderWriter;
import com.siimkinks.sqlitemagic.writer.ValueCreatorWriter;
import com.siimkinks.sqlitemagic.writer.ValueWriter;
import com.squareup.javapoet.TypeName;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import static com.siimkinks.sqlitemagic.util.StringUtil.replaceCamelCaseWithUnderscore;

@EqualsAndHashCode(of = {"tableName"})
public class TableElement {
  @Getter
  private final Environment environment;
  private final PackageElement modelPackage;
  @Getter
  private final TypeElement tableElement;
  @Getter
  private final TypeName tableElementTypeName;
  private final Table tableAnnotation;
  @Getter
  private String tableName;
  private ColumnElement idColumn;
  @Getter
  private boolean immutable;
  @Getter
  private ValueWriter valueWriter;
  private TypeElement $builderElement;
  private boolean hasId;
  @Setter
  @Getter
  private int tablePos;
  @Getter
  private int complexColumnCount = 0;
  @Getter
  private int persistedComplexColumnCount = 0;
  @Getter
  private int persistedImmutableComplexColumnCount = 0;
  @Getter
  private boolean anyUniqueColumnNullable;
  private boolean hasUniqueColumnsOtherThanId = false;
  private Boolean hasAnyNonIdNotNullableColumns;
  private Boolean isQueryPartNeededForShallowQuery;
  Integer graphNodeCount;
  Integer graphAllColumnsCount;
  Integer graphMinimalColumnsCount;
  /**
   * Present only for tables that evaluate to "immutable" in constructor
   */
  @Nullable
  @Setter
  private ImmutableSet<ExecutableElement> allMethods;
  /**
   * Present only for tables that evaluate to "mutable" in constructor
   */
  @Nullable
  @Setter
  private ImmutableSet<VariableElement> allFields;
  /**
   * All columns except id column
   */
  @Getter
  private final List<ColumnElement> columnsExceptId = new ArrayList<>();
  @Getter
  private final List<ColumnElement> allColumns = new ArrayList<>();
  private Set<TableElement> tableTriggers;

  public TableElement(Environment environment, Element tableElement) {
    this.environment = environment;
    this.tableElement = (TypeElement) tableElement;
    this.tableAnnotation = tableElement.getAnnotation(Table.class);
    this.modelPackage = environment.getPackage(tableElement);
    this.tableName = determineTableName(tableElement.getSimpleName().toString(), tableAnnotation.value());
    tableElementTypeName = Environment.getTypeName(this.tableElement);
    collectImmutableObjectMetadataIfNeeded(environment, this.tableElement);
  }

  private void collectImmutableObjectMetadataIfNeeded(Environment environment, TypeElement tableElement) {
    if (environment.hasAutoValueLib()) {
      immutable = tableElement.getAnnotation(environment.getAutoValueAnnotation()) != null;
      if (immutable) {
        final Class<? extends Annotation> builderAnnotation = environment.getAutoValueBuilderAnnotation();
        for (Element e : tableElement.getEnclosedElements()) {
          if (e.getKind() == ElementKind.CLASS && e.getAnnotation(builderAnnotation) != null) {
            $builderElement = (TypeElement) e;
            break;
          }
        }
      }
    }
  }

  public void finishCreate() {
    if (hasBuilder()) {
      valueWriter = ValueBuilderWriter.create(environment,
          $builderElement,
          tableElement.asType(),
          allColumns,
          tableElement.getSimpleName().toString());
    } else if (isImmutable()) {
      valueWriter = ValueCreatorWriter.create(environment,
          allColumns,
          allMethods,
          tableElement.getSimpleName().toString());
    } else if (isValidDataClass()) {
      immutable = true;
      valueWriter = DataClassWriter.create(environment,
          allColumns,
          allFields,
          this,
          tableElement.getSimpleName().toString());
    }
    addMissingColumnsIfNeeded();
  }

  public void collectColumnsMetadata() {
    boolean anyUniqueColumnNullable = false;
    for (ColumnElement columnElement : allColumns) {
      if (columnElement.isReferencedColumn()) {
        this.complexColumnCount++;
      }
      if (columnElement.isHandledRecursively()) {
        this.persistedComplexColumnCount++;
        if (columnElement.isReferencedTableImmutable()) {
          this.persistedImmutableComplexColumnCount++;
        }
      }
      if (columnElement.isUnique()) {
        hasUniqueColumnsOtherThanId = true;
        if (columnElement.isNullable()) {
          anyUniqueColumnNullable = true;
        }
      }
    }
    this.anyUniqueColumnNullable = anyUniqueColumnNullable || idColumn.isNullable();
  }

  @NonNull
  public static String determineTableName(String rawTableElementName, String tableAnnotationValue) {
    if (Strings.isNullOrEmpty(tableAnnotationValue)) {
      return replaceCamelCaseWithUnderscore(rawTableElementName).toLowerCase();
    }
    return tableAnnotationValue;
  }

  public void addColumnElement(ColumnElement columnElement) throws DuplicateException {
    if (columnsExceptId.contains(columnElement)) {
      throw new DuplicateNameException(columnElement.getColumnName());
    }
    if (columnElement.isId()) {
      if (hasId) {
        throw new DuplicateIdException(columnElement.getColumnName());
      }
      this.idColumn = columnElement;
      hasId = true;
    } else {
      columnsExceptId.add(columnElement);
    }
    allColumns.add(columnElement);
    if (columnElement.hasTransformer() && !columnElement.isNullable()) {
      columnElement.getTransformer().markAsCannotTransformNullValues();
    }
  }

  private void addMissingColumnsIfNeeded() {
    if (!hasId && !immutable) {
      allColumns.add(getIdColumn());
    }
  }

  public ColumnElement getIdColumn() {
    if (idColumn == null && !hasId) {
      idColumn = DefaultIdColumnElement.get(environment, this);
    }
    return idColumn;
  }

  public boolean isIdCollected() {
    return idColumn != null;
  }

  @NonNull
  public ColumnElement getColumnByName(@NonNull String columnName) {
    final int dotPos = columnName.indexOf('.');
    if (dotPos != -1) {
      columnName = columnName.substring(dotPos + 1);
    }
    for (ColumnElement columnElement : allColumns) {
      if (columnName.equals(columnElement.getColumnName())) {
        return columnElement;
      }
    }
    throw new RuntimeException("Requested undefined column " + columnName);
  }

  @NonNull
  public FormatData serializedValueGetter(@NonNull String valueGetter) {
    final ColumnElement idColumn = getIdColumn();
    if (idColumn.hasModifier(Modifier.PUBLIC)) {
      return FormatData.create(String.format("%s.%s", valueGetter, idColumn.valueGetter(null)));
    }
    return EntityEnvironment.idGetterFromDao(this, valueGetter);
  }

  public int getAllColumnsCount() {
    return allColumns.size();
  }

  public String getPackageName() {
    return modelPackage.getQualifiedName().toString();
  }

  public String getTableElementName() {
    return tableElement.getSimpleName().toString();
  }

  public FormatData newInstanceField(String entityElementVariableName) {
    return WriterUtil.newInstanceField(entityElementVariableName, tableElementTypeName);
  }

  public boolean persistAll() {
    return tableAnnotation.persistAll();
  }

  public boolean useAccessMethods() {
    return tableAnnotation.useAccessMethods();
  }

  public boolean hasUniqueColumnsOtherThanId() {
    return hasUniqueColumnsOtherThanId;
  }

  public boolean hasAnyComplexColumns() {
    return complexColumnCount > 0;
  }

  public boolean hasAnyPersistedComplexColumns() {
    return persistedComplexColumnCount > 0;
  }

  public boolean hasAnyPersistedImmutableComplexColumns() {
    return persistedImmutableComplexColumnCount > 0;
  }

  public boolean hasBuilder() {
    return $builderElement != null;
  }

  public boolean hasId() {
    return hasId;
  }

  public boolean hasIdSetter() {
    return !isImmutable() || idColumn.isAutoincrementId();
  }

  public boolean canBeInstantiatedWithOnlyId() {
    return !isImmutable() || !hasAnyNonIdNotNullableColumns();
  }

  public Integer getGraphNodeCount() {
    if (graphNodeCount == null) {
      graphNodeCount = ElementGraphWalker.countNodes(this);
    }
    return graphNodeCount;
  }

  public Integer getGraphAllColumnsCount() {
    if (graphAllColumnsCount == null) {
      graphAllColumnsCount = ElementGraphWalker.countAllColumns(this);
    }
    return graphAllColumnsCount;
  }

  public Integer getGraphMinimalColumnsCount() {
    if (graphMinimalColumnsCount == null) {
      graphMinimalColumnsCount = ElementGraphWalker.countMinimalColumns(this);
    }
    return graphMinimalColumnsCount;
  }

  public boolean isQueryPartNeededForShallowQuery() {
    if (isQueryPartNeededForShallowQuery == null) {
      if (hasAnyPersistedImmutableComplexColumns()) {
        for (ColumnElement columnElement : getColumnsExceptId()) {
          if (columnElement.isNeededForShallowQuery()) {
            isQueryPartNeededForShallowQuery = true;
            return true;
          }
        }
      }
      isQueryPartNeededForShallowQuery = false;
      return false;
    }
    return isQueryPartNeededForShallowQuery;
  }

  public boolean hasAnyNonIdNotNullableColumns() {
    if (hasAnyNonIdNotNullableColumns == null) {
      for (ColumnElement columnElement : columnsExceptId) {
        if (!columnElement.isNullable()) {
          hasAnyNonIdNotNullableColumns = Boolean.TRUE;
          break;
        }
      }
      if (hasAnyNonIdNotNullableColumns == null) {
        hasAnyNonIdNotNullableColumns = Boolean.FALSE;
      }
    }
    return hasAnyNonIdNotNullableColumns;
  }

  private boolean isValidDataClass() {
    for (Element enclosedElement : tableElement.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
        ExecutableElement constructor = (ExecutableElement) enclosedElement;
        final List<? extends VariableElement> constructorParams = constructor.getParameters();
        if (constructorParams.size() != allColumns.size()) {
          return false;
        }
        final Types typeUtils = environment.getTypeUtils();
        final Iterator<ColumnElement> columnsIterator = allColumns.iterator();
        for (VariableElement param : constructorParams) {
          final ColumnElement column = columnsIterator.next();
          if (!typeUtils.isSameType(param.asType(), column.getDeserializedType().getTypeMirror())) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  public String getMethodNameForSettingField(String fieldName) {
    final String javaBeansMethodName = "set" + StringUtil.firstCharToUpperCase(fieldName);
    int containingMethodPos = containsAnyMethod(fieldName, javaBeansMethodName);
    switch (containingMethodPos) {
      case 0:
        return fieldName;
      case 1:
        return javaBeansMethodName;
      default:
        return null;
    }
  }

  public String getMethodNameForGettingField(String fieldName, boolean isPrimitiveBoolean) {
    final String capitalizedName = StringUtil.firstCharToUpperCase(fieldName);
    final String javaBeansMethodName = "get" + capitalizedName;
    final String methodName = methodNameFor(fieldName, javaBeansMethodName);
    if (methodName == null && isPrimitiveBoolean) {
      return methodNameFor(fieldName, "is" + capitalizedName);
    }
    return methodName;
  }

  private String methodNameFor(String fieldName, String javaBeansMethodName) {
    final int containingMethodPos = containsAnyMethod(fieldName, javaBeansMethodName);
    switch (containingMethodPos) {
      case 0:
        return fieldName;
      case 1:
        return javaBeansMethodName;
      default:
        return null;
    }
  }

  private int containsAnyMethod(String... methods) {
    final int methodsCount = methods.length;
    return containsAnyMethodRecursively(tableElement, methodsCount, methods);
  }

  private int containsAnyMethodRecursively(TypeElement tableElement, int methodsCount, String[] methods) {
    if (environment.isJavaBaseObject(tableElement.asType())) {
      return -1;
    }
    String elementName;
    for (Element e : tableElement.getEnclosedElements()) {
      if (e.getKind() == ElementKind.METHOD) {
        elementName = e.getSimpleName().toString();
        for (int i = 0; i < methodsCount; i++) {
          if (elementName.equals(methods[i])) {
            return i;
          }
        }
      }
    }
    Element superElement = environment.getTypeUtils().asElement(tableElement.getSuperclass());
    return containsAnyMethodRecursively((TypeElement) superElement, methodsCount, methods);
  }

  public Set<TableElement> getAllTableTriggers() {
    if (this.tableTriggers == null) {
      this.tableTriggers = collectAllTableTriggers();
    }
    return this.tableTriggers;
  }

  private Set<TableElement> collectAllTableTriggers() {
    final Set<TableElement> triggers = new LinkedHashSet<>();
    triggers.add(this);
    traverseAllComplexColumnsAndCollectTableTriggers(triggers, this);
    return triggers;
  }

  static void traverseAllComplexColumnsAndCollectTableTriggers(Set<TableElement> triggers, TableElement table) {
    if (!table.hasAnyPersistedComplexColumns()) {
      return; // we have reached bottom
    }
    for (ColumnElement column : table.getColumnsExceptId()) {
      if (column.isHandledRecursively()) {
        TableElement referencedTable = column.getReferencedTable();
        triggers.add(referencedTable);
        traverseAllComplexColumnsAndCollectTableTriggers(triggers, referencedTable);
      }
    }
  }
}
