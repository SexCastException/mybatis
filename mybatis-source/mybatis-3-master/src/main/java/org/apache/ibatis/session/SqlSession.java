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
package org.apache.ibatis.session;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;

import java.io.Closeable;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 定义了常用的数据库操作以及事务的相关操作，为了方便用户使用，每种类型的操作都提供了多种重载。
 * <p>
 * The primary Java interface for working with MyBatis.
 * Through this interface you can execute commands, get mappers and manage transactions.
 *
 * @author Clinton Begin
 */
public interface SqlSession extends Closeable {

  /**
   * 泛型方法，返回查询的单条记录
   * Retrieve a single row mapped from the statement key.
   *
   * @param <T>       the returned object type
   * @param statement SQL语句
   * @return Mapped object
   */
  <T> T selectOne(String statement);

  /**
   * Retrieve a single row mapped from the statement key and parameter.
   *
   * @param <T>       the returned object type
   * @param statement Unique identifier matching the statement to use. SQL语句
   * @param parameter A parameter object to pass to the statement. SQL语句绑定的实参
   * @return Mapped object
   */
  <T> T selectOne(String statement, Object parameter);

  /**
   * 查询多条记录，并封装成相应类型的结果列表
   * Retrieve a list of mapped objects from the statement key and parameter.
   *
   * @param <E>       the returned list element type
   * @param statement Unique identifier matching the statement to use. SQL语句
   * @return List of mapped object
   */
  <E> List<E> selectList(String statement);

  /**
   * Retrieve a list of mapped objects from the statement key and parameter.
   *
   * @param <E>       the returned list element type
   * @param statement Unique identifier matching the statement to use. SQL语句
   * @param parameter A parameter object to pass to the statement. SQL语句绑定的实参
   * @return List of mapped object
   */
  <E> List<E> selectList(String statement, Object parameter);

  /**
   * Retrieve a list of mapped objects from the statement key and parameter,
   * within the specified row bounds.
   *
   * @param <E>       the returned list element type
   * @param statement Unique identifier matching the statement to use. SQL语句
   * @param parameter A parameter object to pass to the statement. SQL语句绑定的实参
   * @param rowBounds Bounds to limit object retrieval. 用于解析限制结果集的范围
   * @return List of mapped object
   */
  <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds);

  /**
   * The selectMap is a special case in that it is designed to convert a list
   * of results into a Map based on one of the properties in the resulting
   * objects.
   * Eg. Return a of Map[Integer,Author] for selectMap("selectAuthors","id")
   *
   * @param <K>       the returned Map keys type
   * @param <V>       the returned Map values type
   * @param statement Unique identifier matching the statement to use.
   * @param mapKey    The property to use as key for each value in the list.
   * @return Map containing key pair data.
   */
  <K, V> Map<K, V> selectMap(String statement, String mapKey);

  /**
   * The selectMap is a special case in that it is designed to convert a list
   * of results into a Map based on one of the properties in the resulting
   * objects.
   *
   * @param <K>       the returned Map keys type
   * @param <V>       the returned Map values type
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param mapKey    The property to use as key for each value in the list.
   * @return Map containing key pair data.
   */
  <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey);

  /**
   * The selectMap is a special case in that it is designed to convert a list
   * of results into a Map based on one of the properties in the resulting
   * objects.
   *
   * @param <K>       the returned Map keys type
   * @param <V>       the returned Map values type
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param mapKey    The property to use as key for each value in the list.
   * @param rowBounds Bounds to limit object retrieval
   * @return Map containing key pair data.
   */
  <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds);

  /**
   * A Cursor offers the same results as a List, except it fetches data lazily using an Iterator.
   *
   * @param <T>       the returned cursor element type.
   * @param statement Unique identifier matching the statement to use.
   * @return Cursor of mapped objects
   */
  <T> Cursor<T> selectCursor(String statement);

  /**
   * A Cursor offers the same results as a List, except it fetches data lazily using an Iterator.
   *
   * @param <T>       the returned cursor element type.
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @return Cursor of mapped objects
   */
  <T> Cursor<T> selectCursor(String statement, Object parameter);

  /**
   * A Cursor offers the same results as a List, except it fetches data lazily using an Iterator.
   *
   * @param <T>       the returned cursor element type.
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param rowBounds Bounds to limit object retrieval
   * @return Cursor of mapped objects
   */
  <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds);

  /**
   * 查询的结果对象将由此处指定的 {@link ResultHandler}对象处理<br>
   * Retrieve a single row mapped from the statement key and parameter
   * using a {@code ResultHandler}.
   *
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param handler   ResultHandler that will handle each retrieved row
   */
  void select(String statement, Object parameter, ResultHandler handler);

  /**
   * 查询的结果对象将由此处指定的 {@link ResultHandler}对象处理<br>
   * Retrieve a single row mapped from the statement
   * using a {@code ResultHandler}.
   *
   * @param statement Unique identifier matching the statement to use.
   * @param handler   ResultHandler that will handle each retrieved row
   */
  void select(String statement, ResultHandler handler);

  /**
   * 查询的结果对象将由此处指定的 {@link ResultHandler}对象处理<br>
   * Retrieve a single row mapped from the statement key and parameter
   * using a {@code ResultHandler} and {@code RowBounds}.
   *
   * @param statement Unique identifier matching the statement to use.
   * @param rowBounds RowBound instance to limit the query results
   * @param handler   ResultHandler that will handle each retrieved row
   */
  void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler);

  /**
   * 执行insert语句，返回受影响的行数。<br>
   * Execute an insert statement.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @return int The number of rows affected by the insert.
   */
  int insert(String statement);

  /**
   * 执行insert语句，返回受影响的行数。<br>
   * Execute an insert statement with the given parameter object. Any generated
   * autoincrement values or selectKey entries will modify the given parameter
   * object properties. Only the number of rows affected will be returned.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @param parameter A parameter object to pass to the statement.
   * @return int The number of rows affected by the insert.
   */
  int insert(String statement, Object parameter);

  /**
   * 执行update语句，返回受影响的行数。<br>
   * Execute an update statement. The number of rows affected will be returned.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @return int The number of rows affected by the update.
   */
  int update(String statement);

  /**
   * 执行update语句，返回受影响的行数。<br>
   * Execute an update statement. The number of rows affected will be returned.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @param parameter A parameter object to pass to the statement.
   * @return int The number of rows affected by the update.
   */
  int update(String statement, Object parameter);

  /**
   * 执行delete语句，返回受影响的行数。<br>
   * Execute a delete statement. The number of rows affected will be returned.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @return int The number of rows affected by the delete.
   */
  int delete(String statement);

  /**
   * 执行delete语句，返回受影响的行数。<br>
   * Execute a delete statement. The number of rows affected will be returned.
   *
   * @param statement Unique identifier matching the statement to execute.
   * @param parameter A parameter object to pass to the statement.
   * @return int The number of rows affected by the delete.
   */
  int delete(String statement, Object parameter);

  /**
   * 刷新批处理语句并提交数据库连接<br>
   * Flushes batch statements and commits database connection.
   * Note that database connection will not be committed if no updates/deletes/inserts were called.
   * To force the commit call {@link SqlSession#commit(boolean)}
   */
  void commit();

  /**
   * 刷新批处理语句并提交数据库连接<br>
   * Flushes batch statements and commits database connection.
   *
   * @param force forces connection commit
   */
  void commit(boolean force);

  /**
   * 翻译：丢弃挂起的批处理语句并回滚数据库连接。注意，如果没有调用更新/删除/插入，数据库连接将不会回滚。<br>
   * Discards pending batch statements and rolls database connection back.
   * Note that database connection will not be rolled back if no updates/deletes/inserts were called.
   * To force the rollback call {@link SqlSession#rollback(boolean)}
   */
  void rollback();

  /**
   * 翻译：丢弃挂起的批处理语句并回滚数据库连接。注意，如果没有调用更新/删除/插入，数据库连接将不会回滚。<br>
   * Discards pending batch statements and rolls database connection back.
   * Note that database connection will not be rolled back if no updates/deletes/inserts were called.
   *
   * @param force forces connection rollback 是否哦强制回滚事务
   */
  void rollback(boolean force);

  /**
   * 将请求刷新到数据库<br>
   * Flushes batch statements.
   *
   * @return BatchResult list of updated records
   * @since 3.0.6
   */
  List<BatchResult> flushStatements();

  /**
   * 关闭当前Session<br>
   * Closes the session.
   */
  @Override
  void close();

  /**
   * 清空session缓存<br>
   * Clears local session cache.
   */
  void clearCache();

  /**
   * Retrieves current configuration.
   *
   * @return Configuration
   */
  Configuration getConfiguration();

  /**
   * 获取type对应的 {@link org.apache.ibatis.annotations.Mapper}对象<br>
   * Retrieves a mapper.
   *
   * @param <T>  the mapper type
   * @param type Mapper interface class
   * @return a mapper bound to this SqlSession
   */
  <T> T getMapper(Class<T> type);

  /**
   * 获取SqlSession对应的连接<br>
   * Retrieves inner database connection.
   *
   * @return Connection
   */
  Connection getConnection();
}
