package com.siimkinks.sqlitemagic.validator;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.element.ColumnElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.util.StringUtil;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

import lombok.AllArgsConstructor;

/**
 * Modified Tarjan's algorithm for finding strongly-connected components of a graph from
 * <a href="http://algs4.cs.princeton.edu/42digraph/TarjanSCC.java.html">The textbook Algorithms,
 * 4th Edition by Robert Sedgewick and Kevin Wayne</a>
 */
public final class StrongComponentsFinder {

  private final List<TableElement> tableElements;
  private final Digraph digraph;
  private boolean[] marked;        // marked[v] = has v been visited?
  private int[] id;                // id[v] = id of strong component containing v
  private int[] low;               // low[v] = low number of v
  private int pre;                 // preorder number counter
  private int count;               // number of strongly-connected components
  private int relevantCount;        // number of relevant strongly-connected components
  private ArrayDeque<Integer> stack;

  public StrongComponentsFinder(List<TableElement> tableElements, Map<String, Integer> allTableNames) {
    this.tableElements = tableElements;
    final Digraph digraph = new Digraph(tableElements, allTableNames);
    this.digraph = digraph;
    final int vertices = digraph.vertices;
    marked = new boolean[vertices];
    stack = new ArrayDeque<>(vertices);
    id = new int[vertices];
    low = new int[vertices];
    for (int v = 0; v < vertices; v++) {
      if (!marked[v]) dfs(digraph, v);
    }
  }

  private void dfs(Digraph digraph, int v) {
    marked[v] = true;
    low[v] = pre++;
    int min = low[v];
    stack.push(v);
    for (int w : digraph.adj(v)) {
      if (!marked[w]) dfs(digraph, w);
      if (low[w] < min) min = low[w];
    }
    if (min < low[v]) {
      low[v] = min;
      return;
    }
    int w;
    int i = 0;
    do {
      i++;
      w = stack.pop();
      id[w] = count;
      low[w] = digraph.vertices;
    } while (w != v);
    if (i > 1) {
      relevantCount++;
    } else {
      for (Integer integer : digraph.adj(v)) {
        if (integer.equals(v)) {
          relevantCount++;
          break;
        }
      }
    }
    count++;
  }

  public int strongComponentsCount() {
    return relevantCount;
  }

  @SuppressWarnings("unchecked")
  public void printStrongComponents(Messager messager) {
    final int count = this.relevantCount;
    final StringBuilder errBuilder = new StringBuilder("VALIDATION ERROR:\n");
    errBuilder.append("\tTable graph validation failed: Tables cannot have reference cycles.\n\tFound cycles:\n");
    final Queue<Integer>[] components = new Queue[count];
    for (int i = 0; i < count; i++) {
      components[i] = new ArrayDeque<>();
    }
    for (int v = 0; v < digraph.vertices; v++) {
      final int i = id[v];
      if (i < count) {
        components[i].add(v);
      }
    }

    for (int i = 0; i < count; i++) {
      errBuilder.append("\t\t");
      final Queue<Integer> sc = components[i];
      if (sc.size() > 1) {
        StringUtil.join("-", sc, errBuilder, new StringUtil.ToStringCallback<Integer>() {
          @NonNull
          @Override
          public String toString(@NonNull Integer objPos) {
            return tableElements.get(objPos).getTableElementName();
          }
        });
      } else {
        final String tableElementName = tableElements.get(sc.peek()).getTableElementName();
        errBuilder.append(tableElementName)
            .append('-')
            .append(tableElementName);
      }
      errBuilder.append("\n");
    }
    errBuilder.append("\tPossible fix: remove some complex columns or annotate them with @")
        .append(Column.class.getSimpleName())
        .append("(handleRecursively = false)");
    print(messager, errBuilder.toString());
  }

  private void print(Messager messager, String msg, Object... args) {
    if (args.length > 0) msg = String.format(msg, args);
    messager.printMessage(Diagnostic.Kind.ERROR, msg);
  }

  static final class Digraph {
    final int vertices;
    int edges;
    Bag[] adj;
    int[] indegree;

    Digraph(List<TableElement> tableElements, Map<String, Integer> tableNames) {
      int vertices = tableElements.size();
      this.vertices = vertices;
      indegree = new int[vertices];
      adj = new Bag[vertices];
      for (int v = 0; v < vertices; v++) {
        adj[v] = new Bag();
        final TableElement tableElement = tableElements.get(v);
        for (ColumnElement columnElement : tableElement.getColumnsExceptId()) {
          if (!columnElement.isHandledRecursively()) continue;

          final TableElement referencedTable = columnElement.getReferencedTable();
          addEdge(v, tableNames.get(referencedTable.getTableName()));
        }
      }
    }

    // throw an IndexOutOfBoundsException unless {@code 0 <= vertex < vertices}
    private void validateVertex(int vertex) {
      if (vertex < 0 || vertex >= vertices)
        throw new IndexOutOfBoundsException("vertex " + vertex + " is not between 0 and " + (vertices - 1));
    }

    /**
     * Adds the directed edge {@code v->w} to this digraph.
     *
     * @param v the tail vertex
     * @param w the head vertex
     * @throws IndexOutOfBoundsException unless both {@code 0 <= v < V and 0 <= w < V}
     */
    public void addEdge(int v, int w) {
      validateVertex(v);
      validateVertex(w);
      adj[v].add(w);
      indegree[w]++;
      edges++;
    }

    /**
     * Returns the vertices adjacent from vertex <tt>v</tt> in this digraph.
     *
     * @param v the vertex
     * @return the vertices adjacent from vertex <tt>v</tt> in this digraph, as an iterable
     * @throws IndexOutOfBoundsException unless {@code 0 <= vertex < vertices}
     */
    public Iterable<Integer> adj(int v) {
      validateVertex(v);
      return adj[v];
    }

    /**
     * Returns a string representation of the graph.
     *
     * @return the number of vertices <em>V</em>, followed by the number of edges <em>E</em>,
     * followed by the <em>V</em> adjacency lists
     */
    public String toString() {
      final StringBuilder sb = new StringBuilder(String.valueOf(vertices));
      sb.append(" vertices, ")
          .append(edges)
          .append(" edges \n");
      for (int v = 0; v < vertices; v++) {
        sb.append(String.format("%d: ", v));
        for (Integer w : adj[v]) {
          sb.append(String.format("%d ", w));
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  @AllArgsConstructor
  static final class Node {
    final Integer item;
    final Node next;
  }

  static final class Bag implements Iterable<Integer> {
    int size = 0;
    Node first = null;

    public void add(Integer item) {
      Node oldFirst = first;
      first = new Node(item, oldFirst);
      size++;
    }

    @Override
    public Iterator<Integer> iterator() {
      return new ListIterator(first);
    }
  }

  static final class ListIterator implements Iterator<Integer> {
    private Node current;

    public ListIterator(Node first) {
      current = first;
    }

    @Override
    public boolean hasNext() {
      return current != null;
    }

    @Override
    public Integer next() {
      if (!hasNext()) throw new NoSuchElementException();
      int item = current.item;
      current = current.next;
      return item;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
