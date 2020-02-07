/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.executor;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在传统的JDBC编程中，重用 {@link Statement}对象是常用的一种优化手段, 该优化手段可以减少SQL预编译的开销以及创建和
 * 销毁 {@link Statement}对象的开销，从而提高性能。
 * <p>
 * <p>
 * <p>
 * {@link ReuseExecutor}提供了 {@link Statement}重用的功能，{@link ReuseExecutor#statementMap}字段缓存使用过的{@link Statement}
 * 对象，key是SQL语句，value是SQL对应的 {@link Statement}对象。
 * <p>
 * <p>
 * <p>
 * {@link ReuseExecutor#doQuery}、{@link ReuseExecutor#doQueryCursor}和{@link ReuseExecutor#doUpdate}方法的实现与
 * {@link SimpleExecutor}中对应方法的实现基本一样，区别在于其中调用的 prepareStatement()方法，{@link SimpleExecutor}每次都会通过
 * JDBC {@link Connection}创建新的 {@link Statement}对象，而 {@link ReuseExecutor}则会先尝试重用{@link ReuseExecutor#statementMap}
 * 中缓存的 {@link Statement}对象。
 *
 * @author Clinton Begin
 */
public class ReuseExecutor extends BaseExecutor {

  /**
   * 缓存使用过的 {@link Statement}对象，key是SQL语句，value是SQL对应的 {@link Statement}对象。
   */
  private final Map<String, Statement> statementMap = new HashMap<>();

  public ReuseExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.update(stmt);
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.query(stmt, resultHandler);
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.queryCursor(stmt);
  }

  /**
   * 遍历 {@link ReuseExecutor#statementMap}集合并关闭该集合中的 {@link Statement}对象
   *
   * @param isRollback
   * @return
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    // 遍历statementMap集合并关闭该集合中的Statement对象
    for (Statement stmt : statementMap.values()) {
      closeStatement(stmt);
    }
    // 清空statementMap集合
    statementMap.clear();
    // 返回空集合
    return Collections.emptyList();
  }

  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    BoundSql boundSql = handler.getBoundSql();
    // 获取SQL语句
    String sql = boundSql.getSql();
    // 检测是否缓存了相同模式的SQL语句所对应的Statement对象
    if (hasStatementFor(sql)) {
      // 获取statementMap集合中缓存的Statement对象
      stmt = getStatement(sql);
      // 修改超时时间
      applyTransactionTimeout(stmt);
    } else {
      // 获取数据库连接对象
      Connection connection = getConnection(statementLog);
      // 创建新的Statement对象，并缓存到statementMap集合中
      stmt = handler.prepare(connection, transaction.getTimeout());
      putStatement(sql, stmt);
    }
    // 处理占位符
    handler.parameterize(stmt);
    return stmt;
  }

  /**
   * 判断指定的SQL语句是否存在可用的 {@link Statement}对象
   *
   * @param sql
   * @return
   */
  private boolean hasStatementFor(String sql) {
    try {
      // 判断指定的SQL语句是否存在Statement对象，并且数据库连接对象尚未关闭
      return statementMap.keySet().contains(sql) && !statementMap.get(sql).getConnection().isClosed();
    } catch (SQLException e) {
      return false;
    }
  }

  private Statement getStatement(String s) {
    return statementMap.get(s);
  }

  private void putStatement(String sql, Statement stmt) {
    statementMap.put(sql, stmt);
  }

}
