package com.huazai.test.plugin;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 拦截 {@link Executor#query(MappedStatement, Object, RowBounds, ResultHandler)}和 {@link Executor#query(org.apache.ibatis.mapping.MappedStatement, java.lang.Object, org.apache.ibatis.session.RowBounds, org.apache.ibatis.session.ResultHandler)}方法
 *
 * @author pyh
 * @date 2020/2/9 18:09
 */
@Intercepts({
  @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
  @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}
  )})
public class PageInterceptor implements Interceptor {
  // 以下是被拦截方法形参列表中各个参数对应的索引
  private static final int MAPPEDSTATEMENT_INDEX = 0;
  private static final int OBJECT_INDEX = 1;
  private static final int ROWBOUNDS_INDEX = 2;
  private static final int RESULTHANDLER_INDEX = 3;
  private static final int CACHEKEY_INDEX = 4;
  private static final int BOUNDSQL_INDEX = 5;

  private static final String PREFIX = "dialect.";

  private Dialect dialect;

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // 获取被拦截方法参数列表
    Object[] args = invocation.getArgs();
    MappedStatement mappedStatements = (MappedStatement) args[MAPPEDSTATEMENT_INDEX];
    // 形参
    Object parameterObject = args[OBJECT_INDEX];
    RowBounds rowBounds = (RowBounds) args[ROWBOUNDS_INDEX];

    // 获取查询的起始位置
    int offset = rowBounds.getOffset();
    // 获取查询的记录数
    int limit = rowBounds.getLimit();

    // 获取查询的SQL语句
    BoundSql boundSql = mappedStatements.getBoundSql(parameterObject);
    StringBuffer bufferSql = new StringBuffer(boundSql.getSql().trim());
    String sql = getFormatSql(bufferSql.toString().trim());

    // 检测当前使用的数据库产品是否支持分页功能
    if (dialect.supportPage()) {
      // 获取经过处理具有分页功能的SQL语句
      sql = dialect.getPagingSql(sql, offset, limit);
      // 当前拦截的Executor.query()方法中的RowBounds参数不再控制查找结果集的范围，所以要进行重置
      args[ROWBOUNDS_INDEX] = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);
    }

    // 根据当前的SQL语句创建新的MappedStatement对象，并更新到Invocation对象记录的参数列表中
    args[MAPPEDSTATEMENT_INDEX] = createMappedStatement(mappedStatements, boundSql, sql);
    // 通过Invocation.proceed()方法调用被拦截的Executor.query()方法
    return invocation.proceed();
  }

  /**
   * 在处理完拦截到的SQL语句之后，会根据当前的SQL语句创建新的 {@link MappedStatement}对象，并更新到 {@link Invocation}对象
   * 记录的参数列表中。
   *
   * @param mappedStatements
   * @param boundSql
   * @param sql
   * @return
   */
  private Object createMappedStatement(MappedStatement mappedStatements, BoundSql boundSql, String sql) {
    // 为处理后的SQL语句创建新的BoundSql对象，其中会复制原有BoundSql对象的parameterMappings等集合的信息
    BoundSql newBoundSql = createBoundSql(mappedStatements, boundSql, sql);
    return createMappedStatement(mappedStatements, newBoundSql);
  }

  private Object createMappedStatement(MappedStatement mappedStatements, BoundSql boundSql) {
    MappedStatement mappedStatement = new MappedStatement
      .Builder(mappedStatements.getConfiguration(), "", mappedStatements.getSqlSource(), mappedStatements.getSqlCommandType())
      .build();
    return mappedStatement;
  }

  private BoundSql createBoundSql(MappedStatement mappedStatements, BoundSql boundSql, String sql) {
    return boundSql;
  }

  private String getFormatSql(String sql) {
    return sql;
  }

  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  public void setDialect(Dialect dialect) {
    this.dialect = dialect;
  }

  @Override
  public void setProperties(Properties properties) {
    // 查找名称为“dbName”的配置项
    String dbName = (String) properties.get("dbName");

    Map<String, String> result = new HashMap<>();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      String key = (String) entry.getKey();
      if (key != null && key.startsWith(PREFIX)) {
        result.put(key.substring(PREFIX.length()), (String) entry.getValue());
      }
    }

    String dialectClassName = result.get(dbName);
    try {
      Dialect dialect = (Dialect) Class.forName(dialectClassName).newInstance();
      this.setDialect(dialect);
    } catch (Exception e) {
      throw new RuntimeException("Can not find Dialect for " + dbName + "：", e);
    }

  }
}
