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
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实现了批处理多条SQL语句的功能。<br>
 * 应用系统在执行一条SQL语句时，会将SQL语句以及相关参数通过网络发送到数据库系统。对于频繁操作数据库的应用系统来说，
 * 如果执行一条SQL语句就向数据库发送一次请求，很多时间会浪费在网络通信上。使用批量处理的优化方式可以在客户端缓存多条SQL语句，
 * 并在合适的时机将多条SQL语句打包发送给数据库执行，从而减少网络方面的开销，提升系统的性能。<br><br>
 * <p>
 * 在批量执行多条SQL语句时，每次向数据库发送的SQL语句条数是有上限的，如果超过这个上限，数据库会拒绝执行这些SQL语句并抛出异常。
 * 所以批量发送SQL语句的时机很重要。
 * <p>
 * 批处理SQL语句只支持insert、update和delete语句，不支持select类型的语句
 *
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  /**
   * 缓存多个 {@link Statement}对象，其中每个 {@link Statement}对象中都缓存了多条SQL语句
   */
  private final List<Statement> statementList = new ArrayList<>();
  /**
   * 记录批处理的结果，通过 {@link BatchResult#updateCounts}记录每个 {@link Statement}执行批处理的结果
   */
  private final List<BatchResult> batchResultList = new ArrayList<>();
  /**
   * 当前执行的SQL语句
   */
  private String currentSql;
  /**
   * 当前执行的 {@link MappedStatement}对象
   */
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   * 在添加一条SQL语句时，首先会将 {@link BatchExecutor#currentSql}字段记录的SQL语句和{@link BatchExecutor#currentStatement}
   * 字段记录的 {@link MappedStatement}对象与当前添加的SQL和 {@link MappedStatement}对象进行比较，如果相同则添加到同一个
   * {@link Statement}对象中等待执行，如果不同则创建新的 {@link Statement}对象并将其缓存到 {@link BatchExecutor#statementList}
   * 集合中等待执行。（简短而言，多次向数据库发送多条更新语句，如果有连续相同的SQL语句执行，则使用同一份SQL语句，并使用 {@link BatchResult}）
   * 对象保存使用同一份SQL语句不同次数的实参。
   *
   * @param ms
   * @param parameterObject
   * @return
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    // 创建 StatementHandler对象
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    final BoundSql boundSql = handler.getBoundSql();
    // 获取SQL语句
    final String sql = boundSql.getSql();
    final Statement stmt;
    // 判断当前执行的SQL模式与上次执行的SQL模式是否相同且对应的MappedStatement对象是否相同
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      // 获取statementList集合中最后一个Statement对象，也为最近一个使用过的对象
      int last = statementList.size() - 1;
      stmt = statementList.get(last);
      // 设置超时时间
      applyTransactionTimeout(stmt);
      // 绑定实参，处理“?”占位符
      handler.parameterize(stmt);//fix Issues 322
      // 获取最近一次查询保存的结果
      BatchResult batchResult = batchResultList.get(last);
      // 添加本次查询的实参
      batchResult.addParameterObject(parameterObject);
    } else {  // 否则本次查询的SQL语句与最近的一次不相同
      Connection connection = getConnection(ms.getStatementLog());
      // 创建新的Statement对象
      stmt = handler.prepare(connection, transaction.getTimeout());
      // 绑定实参，处理“?”占位符
      handler.parameterize(stmt);    //fix Issues 322
      currentSql = sql;
      currentStatement = ms;
      // 将新创建的Statement对象加入statementList集合
      statementList.add(stmt);
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    // 底层通过调用Statement.addBatch()方法添加SQL语句
    handler.batch(stmt);
    return BATCH_UPDATE_RETURN_VALUE;
  }

  /**
   * 实现逻辑与 {@link SimpleExecutor}类似
   *
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
    throws SQLException {
    Statement stmt = null;
    try {
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   * 实现逻辑与 {@link SimpleExecutor}类似
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * {@link Statement} 中可以添加不同模式的SQL,但是每添加一个新模式的SQL语句都会触发一次编译操作。{@link PreparedStatement}
   * 中只能添加同一模式的SQL语句，只会触发一次编译操作，但是可以通过绑定多组不同的实参实现批处理。{@link BatchExecutor#doUpdate}
   * 将连续添加的、相同模式的SQL语句添加到同一个 {@link Statement}or {@link PreparedStatement}对象中。
   *
   * @param isRollback
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      // 保存批处理的结果
      List<BatchResult> results = new ArrayList<>();
      // 如果明确指定了要回滚事务，则直接返回空集合，忽略statementList集合中记录的sQL语句
      if (isRollback) {
        return Collections.emptyList();
      }
      for (int i = 0, n = statementList.size(); i < n; i++) {
        // 获取Statement对象
        Statement stmt = statementList.get(i);
        // 设置Statement对象的超时时间
        applyTransactionTimeout(stmt);
        BatchResult batchResult = batchResultList.get(i);
        try {
          /*
              调用Statement.executeBatch()方法批量执行其中记录的SQL语句，
              并使用返回的int数组更新BatchResult.updateCounts字段，其中每一个元素都表示一条SQL语句影响的记录条数
          */
          batchResult.setUpdateCounts(stmt.executeBatch());
          MappedStatement ms = batchResult.getMappedStatement();
          // 实参集合
          List<Object> parameterObjects = batchResult.getParameterObjects();
          // 获取配置的KeyGenerator对象
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            // 获取数据库生成的主键，并设置到parameterObjects中
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141
            // 对于非NoKeyGenerator类型的KeyGenerator对象，会调用其processAfter()方法
            for (Object parameter : parameterObjects) {
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          // Close statement to close cursor #1109
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
            .append(" (batch index #")
            .append(i + 1)
            .append(")")
            .append(" failed.");
          if (i > 0) {
            message.append(" ")
              .append(i)
              .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      // 遍历关闭Statement对象
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      // 刷新缓存中的SQL语句后，置空当前执行的SQL语句
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
