package org.apache.ibatis.domain;

public interface Entity<T> {
  T getId();

  void setId(T id);
}
