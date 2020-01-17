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
package org.apache.ibatis.executor.loader;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.ResultExtractor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * 负责保存一次延迟加载操作所需的全部信息
 *
 * @author Clinton Begin
 */
public class ResultLoader {

  /**
   * 全局配置对象
   */
  protected final Configuration configuration;
  /**
   * 执行延迟加载操作的执行器
   */
  protected final Executor executor;
  protected final MappedStatement mappedStatement;
  /**
   * 记录了延迟执行的SQL语句的实参
   */
  protected final Object parameterObject;
  /**
   * 记录了延迟加载得到的对象类型
   */
  protected final Class<?> targetType;
  /**
   * 通过反射创建延迟加载的Java对象
   */
  protected final ObjectFactory objectFactory;
  protected final CacheKey cacheKey;
  /**
   * 记录了延迟执行的SQL语句以及相关配置信息.
   */
  protected final BoundSql boundSql;
  /**
   * 负责将延迟加载得到的结果对象转换成 {@link ResultLoader#targetType} 类型的对象
   */
  protected final ResultExtractor resultExtractor;
  /**
   * 创建 ResultLoader的线程id
   */
  protected final long creatorThreadId;

  protected boolean loaded;
  /**
   * 延迟加载得到的结果对象
   */
  protected Object resultObject;

  public ResultLoader(Configuration config, Executor executor, MappedStatement mappedStatement, Object parameterObject, Class<?> targetType, CacheKey cacheKey, BoundSql boundSql) {
    this.configuration = config;
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.parameterObject = parameterObject;
    this.targetType = targetType;
    this.objectFactory = configuration.getObjectFactory();
    this.cacheKey = cacheKey;
    this.boundSql = boundSql;
    this.resultExtractor = new ResultExtractor(configuration, objectFactory);
    this.creatorThreadId = Thread.currentThread().getId();
  }

  /**
   * 该方法会通过 {@link Executor}执行ResultLoader 中记录的SQL语句并返回相应的延迟加载对象。
   *
   * @return
   * @throws SQLException
   */
  public Object loadResult() throws SQLException {
    List<Object> list = selectList();
    resultObject = resultExtractor.extractObjectFromList(list, targetType);
    return resultObject;
  }

  private <E> List<E> selectList() throws SQLException {
    //
    Executor localExecutor = executor;
    /*
        检测调用该方法的线程是否为创建ResultLoader对象的线程、检测localExecutor是否关闭，
        当检测到异常情况时，会创建新的 Executor 对象来执行延迟加载操作

     */
    if (Thread.currentThread().getId() != this.creatorThreadId || localExecutor.isClosed()) {
      // 创建新的 Executor 对象
      localExecutor = newExecutor();
    }
    try {
      // 执行查询操作，得到延迟加载的对象
      return localExecutor.query(mappedStatement, parameterObject, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER, cacheKey, boundSql);
    } finally {
      if (localExecutor != executor) {
        // 如果是在selectList()方法中新建的Executor对象，则需要关闭
        localExecutor.close(false);
      }
    }
  }

  private Executor newExecutor() {
    final Environment environment = configuration.getEnvironment();
    if (environment == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  Environment was not configured.");
    }
    final DataSource ds = environment.getDataSource();
    if (ds == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  DataSource was not configured.");
    }
    final TransactionFactory transactionFactory = environment.getTransactionFactory();
    final Transaction tx = transactionFactory.newTransaction(ds, null, false);
    return configuration.newExecutor(tx, ExecutorType.SIMPLE);
  }

  public boolean wasNull() {
    return resultObject == null;
  }

}
