package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.transformer.TransformerWithGenerics;

@Table(persistAll = true)
public final class NotPersistedModel2 {
  @Id
  @Column("_id")
  long id;

  TransformerWithGenerics.TransformableObject o1;

  TransformerWithGenerics.TransformableObject2 o2;

  TransformerWithGenerics.TransformableObject3 o3;
}
