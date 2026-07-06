package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseModel baseModel = (BaseModel) o;
    return Objects.equals(baseId, baseModel.baseId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseId);
  }

  @Override
  public String toString() {
    return "BaseModel{" +
        "baseId=" + baseId +
        '}';
  }


}
