package com.siimkinks.sqlitemagic.structure;

import com.siimkinks.sqlitemagic.element.TableElement;

import java.io.Serializable;
import java.util.ArrayList;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public final class TableStructure implements Serializable {
  String name;
  ArrayList<ColumnStructure> columns;

  public static TableStructure create(TableElement tableElement, ArrayList<ColumnStructure> columns) {
    return new TableStructure(tableElement.getTableName(), columns);
  }
}
