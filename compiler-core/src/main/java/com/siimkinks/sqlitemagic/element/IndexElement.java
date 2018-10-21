package com.siimkinks.sqlitemagic.element;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Index;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import lombok.Getter;

import static com.siimkinks.sqlitemagic.element.TableElement.determineTableName;

public class IndexElement {
  private final Environment environment;
  @Getter
  private final Element indexElement;
  private final Index indexAnnotation;
  @Getter
  private final TypeElement tableTypeElement;
  @Getter
  private final boolean unique;
  @Getter
  private final boolean composite;
  @Nullable
  private List<String> indexedColumnNames = null;

  public IndexElement(Environment environment, Element indexElement) {
    this.environment = environment;
    this.indexElement = indexElement;
    this.indexAnnotation = indexElement.getAnnotation(Index.class);
    this.unique = indexAnnotation.unique();
    this.composite = indexElement instanceof TypeElement;
    this.tableTypeElement = getTableTypeElement(composite, indexElement);
  }

  @Nullable
  private static TypeElement getTableTypeElement(boolean composite, Element indexElement) {
    if (composite) {
      return (TypeElement) indexElement;
    } else {
      final Element enclosingElement = indexElement.getEnclosingElement();
      if (enclosingElement != null && enclosingElement instanceof TypeElement) {
        return (TypeElement) enclosingElement;
      }
    }
    return null;
  }

  public String getTableName() {
    final TypeElement tableTypeElement = this.tableTypeElement;
    final Table tableAnnotation = tableTypeElement.getAnnotation(Table.class);
    return determineTableName(tableTypeElement.getSimpleName().toString(), tableAnnotation.value());
  }

  public String getIndexName() {
    final String value = indexAnnotation.value();
    if (Strings.isNullOrEmpty(value)) {
      final String tableName = getTableName();
      final List<String> indexedColumns = getIndexedColumnNames();
      return "index_" + tableName + "_" + Joiner.on("_").join(indexedColumns);
    }
    return value;
  }

  @NonNull
  public List<String> getIndexedColumnNames() {
    if (indexedColumnNames != null) {
      return indexedColumnNames;
    }
    final ArrayList<String> columnNames = new ArrayList<>();
    final TableElement tableElement = getTableElement(environment, getTableName());
    if (tableElement != null) {
      if (composite) {
        final String indexName = indexAnnotation.value();
        for (ColumnElement columnElement : tableElement.getAllColumns()) {
          if (columnElement.getColumnAnnotation().belongsToIndex().equals(indexName)) {
            columnNames.add(columnElement.getColumnName());
          }
        }
      } else {
        final Element indexElement = this.indexElement;
        for (ColumnElement columnElement : tableElement.getAllColumns()) {
          if (columnElement.getColumnElement() == indexElement) {
            columnNames.add(columnElement.getColumnName());
            break;
          }
        }
      }
    }
    indexedColumnNames = columnNames;
    return columnNames;
  }

  @Nullable
  private static TableElement getTableElement(Environment environment, String tableName) {
    for (TableElement tableElement : environment.getAllTableElements()) {
      if (tableElement.getTableName().equals(tableName)) {
        return tableElement;
      }
    }
    return null;
  }

  public String indexSQL() {
    final StringBuilder sb = new StringBuilder("CREATE ");
    if (isUnique()) {
      sb.append("UNIQUE ");
    }
    sb.append("INDEX IF NOT EXISTS ")
        .append(getIndexName())
        .append(" ON ")
        .append(getTableName())
        .append(" (");
    Joiner.on(',').appendTo(sb, getIndexedColumnNames());
    sb.append(")");
    return sb.toString();
  }
}
