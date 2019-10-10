package org.apache.ibatis.domain;

/**
 * 用于测试反射工具
 */
public abstract class AbstractEntity implements Entity<Integer> {
  private Integer id;

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }
}
