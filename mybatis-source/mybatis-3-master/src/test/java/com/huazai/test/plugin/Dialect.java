package com.huazai.test.plugin;

/**
 * @author pyh
 * @date 2020/2/10 17:55
 */
public interface Dialect {
  /**
   * 检测当前使用的数据库产品是否支持分页功能
   *
   * @return
   */
  boolean supportPage();

  /**
   * 根据当前使用的数据库产品，为当前SQL语句添加分页功能，调用该方法之前，需要通过 {@link Dialect#supportPage()}方法确定对应数据库产品支持分页
   *
   * @param sql    sql语句
   * @param offset 查询起始位置
   * @param limit  查询记录数
   * @return
   */
  String getPagingSql(String sql, int offset, int limit);
}
