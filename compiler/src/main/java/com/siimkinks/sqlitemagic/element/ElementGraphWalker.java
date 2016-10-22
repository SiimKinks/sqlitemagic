package com.siimkinks.sqlitemagic.element;

import com.siimkinks.sqlitemagic.util.ConditionCallback;

public class ElementGraphWalker {

  public interface VisitCallback {
    void onVisit(TableElement tableElement);
  }

  public static void depthFirst(TableElement rootElement, VisitCallback visitCallback) {
    if (!rootElement.hasAnyComplexColumns()) {
      return;
    }
    for (ColumnElement element : rootElement.getColumnsExceptId()) {
      if (!element.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = element.getReferencedTable();
      depthFirst(referencedTable, visitCallback);
      visitCallback.onVisit(referencedTable);
    }
  }

  public static void depthFirst(TableElement rootElement, ConditionCallback<TableElement> visitCondition,
                                VisitCallback visitCallback) {
    if (!rootElement.hasAnyComplexColumns()) {
      return;
    }
    for (ColumnElement element : rootElement.getColumnsExceptId()) {
      if (!element.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = element.getReferencedTable();
      if (visitCondition.call(referencedTable)) {
        depthFirst(referencedTable, visitCondition, visitCallback);
        visitCallback.onVisit(referencedTable);
      }
    }
  }

  public static int countNodes(TableElement tableElement) {
    if (!tableElement.hasAnyComplexColumns()) {
      tableElement.graphNodeCount = 0;
      return 0;
    }
    int count = tableElement.getComplexColumnCount();
    for (ColumnElement element : tableElement.getColumnsExceptId()) {
      if (!element.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = element.getReferencedTable();
      if (referencedTable.graphNodeCount == null) {
        count += countNodes(referencedTable);
      } else {
        count += referencedTable.graphNodeCount;
      }
    }
    tableElement.graphNodeCount = count;
    return count;
  }

  public static int countAllColumns(TableElement tableElement) {
    final int allColumnsCount = tableElement.getAllColumnsCount();
    if (!tableElement.hasAnyComplexColumns()) {
      tableElement.graphAllColumnsCount = allColumnsCount;
      return allColumnsCount;
    }
    int count = allColumnsCount;
    for (ColumnElement element : tableElement.getColumnsExceptId()) {
      if (!element.isHandledRecursively()) {
        continue;
      }
      final TableElement referencedTable = element.getReferencedTable();
      if (referencedTable.graphAllColumnsCount == null) {
        count += countAllColumns(referencedTable);
      } else {
        count += referencedTable.graphAllColumnsCount;
      }
    }
    tableElement.graphAllColumnsCount = count;
    return count;
  }

  public static int countMinimalColumns(TableElement tableElement) {
    final int allColumnsCount = tableElement.getAllColumnsCount();
    if (!tableElement.hasAnyComplexColumns()) {
      tableElement.graphMinimalColumnsCount = allColumnsCount;
      return allColumnsCount;
    }
    int count = allColumnsCount;
    for (ColumnElement element : tableElement.getColumnsExceptId()) {
      if (!element.isHandledRecursively() || !element.isNeededForShallowQuery()) {
        continue;
      }
      final TableElement referencedTable = element.getReferencedTable();
      if (referencedTable.graphMinimalColumnsCount == null) {
        count += countMinimalColumns(referencedTable);
      } else {
        count += referencedTable.graphMinimalColumnsCount;
      }
    }
    tableElement.graphMinimalColumnsCount = count;
    return count;
  }
}
