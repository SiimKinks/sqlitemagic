package com.siimkinks.sqlitemagic.structure;

import com.siimkinks.sqlitemagic.element.ColumnElement;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public final class ColumnStructure implements Serializable {
  boolean id;
  boolean autoIncrement;
  String name;
  boolean onDeleteCascade;
  String sqlType;
  String schema;

  public static ColumnStructure create(ColumnElement columnElement) {
    return new ColumnStructure(columnElement.isId(),
        columnElement.isAutoincrementId(),
        columnElement.getColumnName(),
        columnElement.isOnDeleteCascade(),
        ColumnElement.getSqlTypeFromTypeElement(columnElement.getSerializedType()),
        columnElement.getSchema());
  }
}
