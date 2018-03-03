package com.siimkinks.sqlitemagic.structure;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.IndexElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatabaseStructure implements Serializable {
  Map<String, TableStructure> tables = emptyMap();
  Set<String> views = emptySet();
  Map<String, IndexStructure> indices = emptyMap();

  public static DatabaseStructure create(Environment environment, GenClassesManagerStep managerStep) {
    final List<TableElement> allTableElements = environment.getAllTableElements();
    final HashMap<String, TableStructure> tables = new HashMap<>(allTableElements.size());
    for (TableElement tableElement : allTableElements) {
      final List<ColumnElement> allColumns = tableElement.getAllColumns();
      final ArrayList<ColumnStructure> columns = new ArrayList<>(allColumns.size());
      for (ColumnElement columnElement : allColumns) {
        columns.add(ColumnStructure.create(columnElement));
      }
      tables.put(tableElement.getTableName(), TableStructure.create(tableElement, columns));
    }

    final Collection<ViewElement> allViewElements = managerStep.getAllViewElements();
    final Set<String> views = new HashSet<>(allViewElements.size());
    for (ViewElement viewElement : allViewElements) {
      views.add(viewElement.getViewName());
    }

    final List<IndexElement> allIndexElements = managerStep.getAllIndexElements();
    final HashMap<String, IndexStructure> indices = new HashMap<>(allIndexElements.size());
    for (IndexElement indexElement : allIndexElements) {
      indices.put(indexElement.getIndexName(), IndexStructure.create(indexElement));
    }

    return new DatabaseStructure(tables, views, indices);
  }
}
