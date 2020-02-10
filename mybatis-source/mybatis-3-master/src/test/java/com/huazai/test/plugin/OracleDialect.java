package com.huazai.test.plugin;


/**
 * @author pyh
 * @date 2020/2/10 19:02
 */
public class OracleDialect implements Dialect {
  public static final String FOR_UPDATE = "for update";

  @Override
  public boolean supportPage() {
    return true;
  }

  @Override
  public String getPagingSql(String sql, int offset, int limit) {
    sql = sql.trim();
    // 记录当前select语句是否包含“for update”子句，该子句会对数据行加锁
    boolean hasForUpdate = false;
    if (sql.toLowerCase().endsWith(FOR_UPDATE)) {
      // 将当前SQL语句的“for update”片段删除
      sql = sql.substring(0, sql.length() - FOR_UPDATE.length());
      hasForUpdate = true;
    }

    // result用于记录添加分页支持之后的SQL语句，这里预先将StringBuffer扩充到合理的值
    StringBuffer result = new StringBuffer(sql.length() + 100);
    // 根据offset值拼接支持分页的SQL语句的前半段
    if (offset > 0) {
      result.append("select * from ( select row_.*, rownum_ from ( ");
    } else {
      result.append("select * from ( ");
    }
    // 将原有的SQL语句拼接到result中
    result.append(sql);

    // 根据offset值拼接支持分页的SQL语句的后半段
    if (offset > 0) {
      result.append(" ) row_ ) where rownum_ <= " + offset + "+" + limit + " and rownum_ > " + offset);
    } else {
      result.append(" ) where rownum <= " + limit);
    }

    // 根据前面记录的hasForUpdate标志，决定是否复原“for update”子句
    if (hasForUpdate) {
      result.append(" for update");
    }
    return result.toString();
  }
}
