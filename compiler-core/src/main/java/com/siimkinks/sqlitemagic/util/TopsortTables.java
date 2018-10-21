package com.siimkinks.sqlitemagic.util;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public final class TopsortTables implements Iterable<TableElement> {
  private final LinkedList<TableElement> sorted;

  @NonNull
  @CheckResult
  public static TopsortTables sort(@NonNull Environment environment) {
    final List<TableElement> allTableElements = environment.getAllTableElements();
    return sort(environment, allTableElements);
  }

  @NonNull
  @CheckResult
  public static TopsortTables sort(@NonNull Environment environment, List<TableElement> allTableElements) {
    final Map<String, Integer> tableNamesToPositions = environment.getAllTableNames();
    final ArrayList<Node> nodes = new ArrayList<>(allTableElements.size());
    for (TableElement element : allTableElements) {
      nodes.add(new Node(element, tableNamesToPositions, nodes));
    }
    return new TopsortTables(nodes);
  }

  private TopsortTables(@NonNull ArrayList<Node> nodes) {
    final LinkedList<TableElement> sorted = new LinkedList<>();
    for (Node node : nodes) {
      visit(node, sorted);
    }
    this.sorted = sorted;
  }

  private void visit(@NonNull Node node, @NonNull LinkedList<TableElement> sorted) {
    if (node.temporaryMark) {
      throw new IllegalStateException("Cyclic graph");
    }
    if (!node.visited) {
      node.temporaryMark = true;
      for (Node m : node) {
        visit(m, sorted);
      }
      node.visited = true;
      node.temporaryMark = false;
      sorted.addFirst(node.element);
    }
  }

  @Override
  public Iterator<TableElement> iterator() {
    // tables need to be created in reverse order
    return sorted.descendingIterator();
  }

  static class Node implements Iterable<Node> {
    @NonNull
    final TableElement element;
    @NonNull
    final Map<String, Integer> tableNamesToPositions;
    @NonNull
    final ArrayList<Node> nodes;
    boolean temporaryMark = false;
    boolean visited = false;

    Node(@NonNull TableElement element,
         @NonNull Map<String, Integer> tableNamesToPositions,
         @NonNull ArrayList<Node> nodes) {
      this.element = element;
      this.tableNamesToPositions = tableNamesToPositions;
      this.nodes = nodes;
    }

    @Override
    public Iterator<Node> iterator() {
      final List<ColumnElement> columns = element.getColumnsExceptId();
      final int size = columns.size();
      return new Iterator<Node>() {
        int cursor = 0;
        int nextNodePos;

        @Override
        public boolean hasNext() {
          for (int i = cursor; i < size; i++) {
            final ColumnElement columnElement = columns.get(i);
            if (!columnElement.isHandledRecursively()) continue;

            final TableElement referencedTable = columnElement.getReferencedTable();
            nextNodePos = tableNamesToPositions.get(referencedTable.getTableName());
            cursor = i + 1;
            return true;
          }
          return false;
        }

        @Override
        public Node next() {
          return nodes.get(nextNodePos);
        }
      };
    }
  }
}
