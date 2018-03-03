package com.siimkinks.sqlitemagic.structure;

import com.siimkinks.sqlitemagic.element.IndexElement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class IndexStructure {
  String name;
  String indexSql;
  String forTable;

  public static IndexStructure create(IndexElement indexElement) {
    return new IndexStructure(
        indexElement.getIndexName(),
        indexElement.indexSQL(),
        indexElement.getTableName());
  }
}
