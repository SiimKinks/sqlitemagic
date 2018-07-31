package com.siimkinks.sqlitemagic.structure;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.IndexElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.util.TopsortTables;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatabaseStructure implements Serializable {
  LinkedHashMap<String, TableStructure> tables = new LinkedHashMap<>();
  LinkedHashMap<String, IndexStructure> indices = new LinkedHashMap<>();

  public static DatabaseStructure create(Environment environment, GenClassesManagerStep managerStep) {
    final List<TableElement> allTableElements = environment.getAllTableElements();
    final LinkedHashMap<String, TableStructure> tables = new LinkedHashMap<>(allTableElements.size());
    for (TableElement tableElement : TopsortTables.sort(environment, allTableElements)) {
      final List<ColumnElement> allColumns = tableElement.getAllColumns();
      final ArrayList<ColumnStructure> columns = new ArrayList<>(allColumns.size());
      for (ColumnElement columnElement : allColumns) {
        columns.add(ColumnStructure.create(columnElement));
      }
      tables.put(tableElement.getTableName(), TableStructure.create(tableElement, columns));
    }

    final List<IndexElement> allIndexElements = managerStep.getAllIndexElements();
    final LinkedHashMap<String, IndexStructure> indices = new LinkedHashMap<>(allIndexElements.size());
    for (IndexElement indexElement : allIndexElements) {
      indices.put(indexElement.getIndexName(), IndexStructure.create(indexElement));
    }

    return new DatabaseStructure(tables, indices);
  }

  public DatabaseStructure plus(DatabaseStructure other) {
    final LinkedHashMap<String, TableStructure> tables = new LinkedHashMap<>(getTables());
    tables.putAll(other.getTables());

    final LinkedHashMap<String, IndexStructure> indices = new LinkedHashMap<>(getIndices());
    indices.putAll(other.getIndices());

    return new DatabaseStructure(tables, indices);
  }
}
