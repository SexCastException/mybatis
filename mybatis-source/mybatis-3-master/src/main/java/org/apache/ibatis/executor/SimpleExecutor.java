/**
 * Copyright 2009-2019 the original author or authors.
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
import java.util.List;

/**
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      /*
        创建StatementHandler对象,实际返回的是RoutingStatementHandler对象，
        其中根据MappedStatement.statementType选择具体的StatementHandler实现
      */
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // 完成Statement的创建和初始化，并处理占位符
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行SQL语句
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      /*
        创建StatementHandler对象,实际返回的是RoutingStatementHandler对象，
        其中根据MappedStatement.statementType选择具体的StatementHandler实现
      */
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      // 完成Statement的创建和初始化，并处理占位符
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行SQL语句，并通过ResultSetHandler完成结果集的映射
      return handler.query(stmt, resultHandler);
    } finally {
      // 关闭Statement对象
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    /*
        创建StatementHandler对象,实际返回的是RoutingStatementHandler对象，
        其中根据MappedStatement.statementType选择具体的StatementHandler实现
    */
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    // 完成Statement的创建和初始化，并处理占位符
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    // 执行SQL语句并返回相应的游标对象
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 该类不提供批量处理SQL语句的功能，所以该方法直接返回空集合
   *
   * @param isRollback
   * @return
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 生成 {@link Statement}对象，并处理sql语句中的占位符
   *
   * @param handler
   * @param statementLog
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    // 获取数据库连接
    Connection connection = getConnection(statementLog);
    // 创建Statement对象
    stmt = handler.prepare(connection, transaction.getTimeout());
    // 处理占位符
    handler.parameterize(stmt);
    return stmt;
  }

}
