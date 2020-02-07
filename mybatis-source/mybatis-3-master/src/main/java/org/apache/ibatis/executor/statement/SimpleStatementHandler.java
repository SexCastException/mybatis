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
package org.apache.ibatis.executor.statement;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 继承了 {@link BaseStatementHandler} 抽象类。它底层使用 {@link Statement}对象来完成数据库的相关操作，
 * 所以SQL语句中不能存在占位符，相应的，{@link SimpleStatementHandler#parameterize}方法是空实现。
 *
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 负责执行insert、update和delete语句，并且根据配置的 {@link KeyGenerator}获取数据库生成的主键
   *
   * @param statement
   * @return
   * @throws SQLException
   */
  @Override
  public int update(Statement statement) throws SQLException {
    // 获取SQL语句
    String sql = boundSql.getSql();
    // 获取实参
    Object parameterObject = boundSql.getParameterObject();
    // 获取配置的KeyGenerator对象
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // 执行SQL语句并返回主键生成
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      // 获取受影响的行数
      rows = statement.getUpdateCount();
      // 将数据库生成的主键设置到实参parameterObject
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      // 执行SQL语句
      statement.execute(sql);
      // 获取受影响的行数
      rows = statement.getUpdateCount();
      // 执行<selectKey>节点中配置的SQL语句获取数据库生成的主键，并添加到parameterObject中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {  // 没有配置KeyGenerator，直接执行SQL语句
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }

  /**
   * 完成数据库的查询操作，并通过 {@link ResultSetHandler}对象将结果集映射成结果对象
   *
   * @param statement
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    // 获取SQL语句
    String sql = boundSql.getSql();
    // 执行SQL语句
    statement.execute(sql);
    // 映射结果集
    return resultSetHandler.handleResultSets(statement);
  }

  /**
   * 完成数据库的查询操作，并通过 {@link ResultSetHandler}对象返回 {@link Cursor}对象
   *
   * @param statement
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleCursorResultSets(statement);
  }

  /**
   * 直接通过 JDBC的{@link Connection}对象创建 {@link Statement}对象
   *
   * @param connection
   * @return
   * @throws SQLException
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.createStatement();
    } else {
      // 设置结果集是否可以滚动及其游标是否可以上下移动，设置结果集是否可更新
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  /**
   * 空实现
   *
   * @param statement
   */
  @Override
  public void parameterize(Statement statement) {
    // N/A
  }

}
