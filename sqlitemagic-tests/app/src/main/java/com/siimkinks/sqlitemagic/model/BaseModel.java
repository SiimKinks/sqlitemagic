package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public abstract class BaseModel {

  @Id(autoIncrement = true)
  @Column(useAccessMethods = true)
  private Long baseId;

  public Long getBaseId() {
    return baseId;
  }

  public void setBaseId(Long baseId) {
    this.baseId = baseId;
  }


}
