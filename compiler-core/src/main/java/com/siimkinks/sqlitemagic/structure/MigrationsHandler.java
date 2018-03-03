package com.siimkinks.sqlitemagic.structure;

import android.support.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.util.JsonConfig;
import com.siimkinks.sqlitemagic.util.TopsortTables;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.Diagnostic;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class MigrationsHandler {
  private final Environment environment;
  private final GenClassesManagerStep managerStep;

  public static void handleMigrations(Environment environment, GenClassesManagerStep managerStep) {
    new MigrationsHandler(environment, managerStep).handleMigrations();
  }

  private void handleMigrations() {
    try {
      final DatabaseStructure currentStructure = DatabaseStructure.create(environment, managerStep);
      final DatabaseStructure previousStructure = readPreviousStructure(environment);
      if (previousStructure != null && !currentStructure.equals(previousStructure)) {
        migrateDatabase(previousStructure, currentStructure);
      }
      persistLatestStructure(currentStructure, environment);
    } catch (Exception e) {
      new RuntimeException("Failed to automatically migrate database", e).printStackTrace();
    }
  }

  @Nullable
  private DatabaseStructure readPreviousStructure(Environment environment) {
    try {
      final File latestStructDir = latestStructureDir(environment);
      final File latestStructureFile = latestStructureFile(latestStructDir);
      return JsonConfig.OBJECT_MAPPER.readValue(latestStructureFile, DatabaseStructure.class);
    } catch (IOException e) {
      return null;
    }
  }

  private void persistLatestStructure(DatabaseStructure structure, Environment environment) {
    try {
      final File latestStructDir = latestStructureDir(environment);
      if (!latestStructDir.exists()) {
        latestStructDir.mkdirs();
      }
      final File latestStructureFile = latestStructureFile(latestStructDir);
      JsonConfig.OBJECT_MAPPER.writeValue(latestStructureFile, structure);
    } catch (IOException e) {
      environment.getMessager().printMessage(Diagnostic.Kind.WARNING, "Error persisting latest schema graph");
    }
  }

  private void migrateDatabase(DatabaseStructure from, DatabaseStructure to) {
    final ArrayList<String> migrationStatements = new ArrayList<>();
    final ArrayList<String> tableMigrationStatements = new ArrayList<>();
    final Set<String> changedTables = migrateTables(from, to, tableMigrationStatements);
    dropIndices(from, to, changedTables, migrationStatements);
    migrationStatements.addAll(tableMigrationStatements);
    createIndices(from, to, changedTables, migrationStatements);
    // TODO migrate views -- drop all views?

    if (environment.isSubmodule()) {
      // TODO persist migration statements
    } else {
      // TODO read submodules statements
      persistMigrationStatements(migrationStatements);
    }
  }

  private Set<String> migrateTables(DatabaseStructure from, DatabaseStructure to, ArrayList<String> migrationStatements) {
    final Map<String, TableStructure> fromTables = from.tables;
    final Map<String, TableStructure> toTables = to.tables;
    final Set<String> changedTables = new HashSet<>();
    final Set<String> newTables = new HashSet<>();

    final Map<List<ColumnStructure>, String> fromTablesStructures = destructedTablesStructures(fromTables);
    for (Map.Entry<String, TableStructure> entry : toTables.entrySet()) {
      final String tableName = entry.getKey();
      if (fromTables.containsKey(tableName)) {
        final TableStructure fromTableStructure = fromTables.get(tableName);
        final TableStructure toTableStructure = entry.getValue();
        if (!toTableStructure.equals(fromTableStructure)) {
          changedTables.add(tableName);
          handleTableChange(fromTableStructure, toTableStructure, migrationStatements);
        } // else unchanged
      } else {
        final String changedTableName = fromTablesStructures.get(entry.getValue().columns);
        if (changedTableName != null) {
          changedTables.add(changedTableName);
          migrationStatements.add("ALTER TABLE " + changedTableName + " RENAME TO " + tableName);
        } else {
          // create table
          newTables.add(tableName);
        }
      }
    }

    final HashSet<String> removedTables = new HashSet<>(fromTables.keySet());
    removedTables.removeAll(toTables.keySet());
    removedTables.removeAll(changedTables);
    for (String removedTable : removedTables) {
      migrationStatements.add("DROP TABLE IF EXISTS " + removedTable);
    }
    changedTables.addAll(removedTables);

    final ArrayList<TableElement> newTableElements = new ArrayList<>(environment.getAllTableElements());
    final Iterator<TableElement> iterator = newTableElements.iterator();
    while (iterator.hasNext()) {
      final TableElement next = iterator.next();
      if (!newTables.contains(next.getTableName())) {
        iterator.remove();
      }
    }
    for (TableElement tableElement : TopsortTables.sort(environment, newTableElements)) {
      migrationStatements.add(tableElement.getSchema());
    }

    return changedTables;
  }

  private void handleTableChange(TableStructure fromTableStructure, TableStructure toTableStructure, ArrayList<String> migrationStatements) {
    final ArrayList<ColumnStructure> fromColumns = fromTableStructure.columns;
    final ArrayList<ColumnStructure> toColumns = toTableStructure.columns;
    final String tableName = toTableStructure.name;
    final int fromColumnsSize = fromColumns.size();
    final int toColumnsSize = toColumns.size();
    if (fromColumnsSize < toColumnsSize) {
      boolean allFromColumnsMatchWithToColumns = true;
      for (int i = 0; i < fromColumnsSize; i++) {
        final ColumnStructure fromColumn = fromColumns.get(i);
        final ColumnStructure toColumn = toColumns.get(i);
        if (!fromColumn.equals(toColumn)) {
          allFromColumnsMatchWithToColumns = false;
          break;
        }
      }
      if (allFromColumnsMatchWithToColumns) {
        for (int i = fromColumnsSize; i < toColumnsSize; i++) {
          final ColumnStructure newColumn = toColumns.get(i);
          migrationStatements.add("ALTER TABLE " +
              tableName +
              " ADD COLUMN " +
              newColumn.schema);
        }
        return;
      }
    }

    final String tmpTableName = tableName + "_";
    migrationStatements.add("ALTER TABLE " + tableName + " RENAME TO " + tmpTableName);
    migrationStatements.add(toTableStructure.schema);

    final String mutualColumns = Joiner
        .on(',')
        .join(mutualColumns(fromTableStructure, toTableStructure));
    migrationStatements.add("INSERT INTO " +
        tableName +
        " (" +
        mutualColumns +
        ") SELECT " +
        mutualColumns +
        " FROM " +
        tmpTableName);
    migrationStatements.add("DROP TABLE IF EXISTS " + tmpTableName);
  }

  private static Map<List<ColumnStructure>, String> destructedTablesStructures(Map<String, TableStructure> tables) {
    final HashMap<List<ColumnStructure>, String> result = new HashMap<>(tables.size());
    for (TableStructure tableStructure : tables.values()) {
      result.put(tableStructure.columns, tableStructure.name);
    }
    return result;
  }

  private static List<String> mutualColumns(TableStructure s1, TableStructure s2) {
    final HashSet<String> s2Columns = new HashSet<>(s2.columns.size());
    for (ColumnStructure column : s2.columns) {
      s2Columns.add(column.name);
    }
    final ArrayList<String> mutualColumns = new ArrayList<>(s1.columns.size());
    for (ColumnStructure column : s1.columns) {
      mutualColumns.add(column.name);
    }
    mutualColumns.retainAll(s2Columns);
    return mutualColumns;
  }

  private void dropIndices(DatabaseStructure from, DatabaseStructure to, Set<String> changedTables, ArrayList<String> migrationStatements) {
    final Map<String, IndexStructure> fromIndices = from.indices;
    final Map<String, IndexStructure> toIndices = to.indices;
    final Set<String> droppedIndices = new HashSet<>();
    for (String fromIndex : fromIndices.keySet()) {
      if (!toIndices.containsKey(fromIndex)) {
        droppedIndices.add(fromIndex);
      }
    }
    for (IndexStructure indexStructure : toIndices.values()) {
      if (changedTables.contains(indexStructure.forTable)) {
        droppedIndices.add(indexStructure.name);
      }
    }

    for (String index : droppedIndices) {
      migrationStatements.add("DROP INDEX IF EXISTS " + index);
    }
  }

  private void createIndices(DatabaseStructure from, DatabaseStructure to, Set<String> changedTables, ArrayList<String> migrationStatements) {
    final Map<String, IndexStructure> fromIndices = from.indices;
    final Map<String, IndexStructure> toIndices = to.indices;
    final Set<IndexStructure> createdIndices = new HashSet<>();
    for (Map.Entry<String, IndexStructure> toIndex : toIndices.entrySet()) {
      if (!fromIndices.containsKey(toIndex.getKey())) {
        createdIndices.add(toIndex.getValue());
      }
    }
    for (IndexStructure indexStructure : toIndices.values()) {
      if (changedTables.contains(indexStructure.forTable)) {
        createdIndices.add(indexStructure);
      }
    }

    for (IndexStructure createdIndex : createdIndices) {
      migrationStatements.add(createdIndex.indexSql);
    }
  }

  private void persistMigrationStatements(ArrayList<String> migrationStatements) {
    if (migrationStatements.isEmpty()) {
      return;
    }
    final Integer dbVersion = environment.getDbVersion();

    final File assetsDir = new File(environment.getProjectDir(),
        "src" + File.separatorChar +
            environment.getVariantDirName() + File.separatorChar +
            "assets");
    if (!assetsDir.exists()) {
      assetsDir.mkdirs();
    }
    final File migrationFile = new File(assetsDir, dbVersion + ".sql");
    try {
      Files.asCharSink(migrationFile, Charset.forName("UTF-8"))
          .writeLines(migrationStatements);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static File latestStructureDir(Environment environment) {
    return new File(environment.getProjectDir(), "db");
  }

  private static File latestStructureFile(File latestStructureDir) {
    return new File(latestStructureDir, "latest.struct");
  }
}
