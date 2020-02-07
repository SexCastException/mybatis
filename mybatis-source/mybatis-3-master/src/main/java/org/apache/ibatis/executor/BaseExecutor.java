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

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

/**
 * 实现了 {@link Executor}接口的大部分方法，提供了缓存管理和事务管理的基本功能
 * <p>
 * 设计模式：模板方法模式和装饰模式 <br><br>
 * <p>
 * 一级缓存是会话级别的缓存，在MyBatis中每创建一个 {@link SqlSession} 对象，就表示开启一次数据库会话。<br><br>
 * <p>
 * 在执行查询操作时，会先查询一级缓存，如果其中存在完全一样的查询语句，则直接从一级缓存中取出相应的结果对象并返回给用户，这样不需要再访
 * 问数据库了，从而减小了数据库的压力。<br><br>
 * <p>
 * 一级缓存的生命周期与{@link SqlSession}相同，其实也就与{@link SqlSession}中封装的 {@link Executor} 对象的生命周期相同。
 * 当调用 {@link Executor#close(boolean)}方法时，该Executor对象对应的一级缓存就变得不可用。<br><br>
 * <p>
 * 一级缓存中对象的存活时间受很多方面的影响，例如，在调用 {@link Executor#update}方法时，也会先清空一级缓存。<br><br>
 *
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  /**
   * 实现事务的提交、回滚和关闭操作
   */
  protected Transaction transaction;
  /**
   * 被装饰的 {@link Executor}对象
   */
  protected Executor wrapper;

  /**
   * 延迟加载队列
   */
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  /**
   * 一级缓存，用于缓存该 {@link Executor}对象查询结果集映射得到的结果对象。
   */
  protected PerpetualCache localCache;
  /**
   * 一级缓存，用于缓存输出类型的参数
   */
  protected PerpetualCache localOutputParameterCache;
  protected Configuration configuration;

  /**
   * 用来记录嵌套查询的层数
   */
  protected int queryStack;
  /**
   * 当前Executor对象是否已关闭，默认false
   */
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  /**
   * 获取事务
   *
   * @return
   */
  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  /**
   * 关闭执行器，在这之前先回滚事务和关闭事务
   *
   * @param forceRollback 决定是否真正回滚事务
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore.  There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  /**
   * 判断执行器是否已关闭
   *
   * @return
   */
  @Override
  public boolean isClosed() {
    return closed;
  }

  /**
   * 执行更新操作（insert、update和delete），在这之前会先清空缓存，避免脏读
   *
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  /**
   * 执行缓存的SQL语句
   *
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  /**
   * 主要针对批处理Executor缓存的多条SQL语句
   *
   * @param isRollBack
   * @return
   * @throws SQLException
   */
  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    // 判断执行器是否已经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  /**
   * 首先创建 {@link CacheKey}对象，并根据该 {@link CacheKey}对象查找一级缓存，如果缓存命中则返回缓存中记录的结果对象，
   * 如果缓存未命中则查询数据库得到结果集，之后将结果集映射成结果对象并保存到一级缓存中。
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获取BoundSql对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 创建CacheKey对象
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    // 调用另外一个重载方法查询数据
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 根据 {@link CacheKey}对象查询一级缓存，如果缓存命中则将缓存中记录的结果对象返回,如果缓存未命中，
   * 则调用 {@link BaseExecutor#doQuery}方法完成数据库的查询操作并得到结果对象，之后将结果对象记录到一级缓存中。<br>
   * 如果一级缓存中缓存了嵌套查询的结果对象，则可以从一级缓存中直接加载该结果对象；如果一级缓存中记录的嵌套查询的结果
   * 对象并未完全加载，则可以通过 {@link DeferredLoad}实现类似延迟加载的功能。
   * <p>
   * 决定清空一级缓存的两项配置：1、&lt;select>节点的flushCache 2、全局配置文件的localCacheScope的配置
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    // 检测当前executor是否已关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // queryStack为0，表示没有嵌套
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      // 非嵌套查询，并且<select>节点的flushCache属性为true时，才会清空一级缓存
      clearLocalCache();
    }
    List<E> list;
    try {
      // 增加查询层数
      queryStack++;
      // 查询一级缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) { // 缓存命中，则处理输出参数
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {  // 缓存未命中，则从数据库中查询数据
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally { // 当前查询完成，查询层数减少
      queryStack--;
    }
    if (queryStack == 0) {
      /*
          在最外层的查询结束时，所有嵌套查询也已经完成，相关缓存项也已经完全加载，所以在这里可以
          触发DeferredLoad加载一级缓存中记录的嵌套查询的结果对象
      */
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      // 缓存加载完后，清空deferredLoads集合
      deferredLoads.clear();
      // 根据localCacheScope配置决定是否清空一级缓存
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  /**
   * 数据库查询数据，将结果封装成{@link Cursor}对象返回，待用户遍历 {@link Cursor}时才真正完成结果集的映射操作
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * 创建 {@link DeferredLoad}对象，并将其添加到 {@link BaseExecutor#deferredLoads}集合中
   *
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    // 检测executor对象是否已关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      // 一级缓存中已经记录了指定查询的结果对象，直接从缓存中加载对象，并设置到外层对象中
      deferredLoad.load();
    } else {
      // 将DeferredLoad对象添加到deferredLoads队列中，待整个外层查询结束后，再加载该结果对象
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  /**
   * 创建 {@link CacheKey}对象，该对象由 {@link MappedStatement}的id、对应的offset和limit、SQL语句(包含“?”占位符)、
   * 用户传递的实参以及 {@link Environment}的id这五部分构成。
   *
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param boundSql
   * @return
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    // 检测当前Executor对象是否已关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 创建CacheKey对象
    CacheKey cacheKey = new CacheKey();
    // 将MappedStatement的id添加到CacheKey对象中
    cacheKey.update(ms.getId());
    // 将offset添加到CacheKey对象中
    cacheKey.update(rowBounds.getOffset());
    // 将limit添加到CacheKey对象中
    cacheKey.update(rowBounds.getLimit());
    // 将SQL语句添加到CacheKey对象中
    cacheKey.update(boundSql.getSql());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // mimic DefaultParameterHandler logic
    // 获取用户传入的实参，并添加到CacheKey对象中
    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {  // 过滤掉输出类型的参数
        Object value;
        String propertyName = parameterMapping.getProperty();
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        // 将实参添加到CacheKey对象中
        cacheKey.update(value);
      }
    }
    // 将数据库环境添加到CacheKey对象中
    if (configuration.getEnvironment() != null) {
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  /**
   * 检测本地缓存中是否缓存了指定的 {@link CacheKey}指定的缓存对象
   *
   * @param ms
   * @param key
   * @return
   */
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  /**
   * 提交事务，在这之前先清空缓存和执行缓存中的SQL语句
   *
   * @param required 决定是否真正提交事务
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  /**
   * 回滚事务，在这之前先清空缓存和执行缓存中的SQL语句
   *
   * @param required 决定是否真正回滚事务
   * @throws SQLException
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        clearLocalCache();
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  /**
   * 清空本地缓存
   */
  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter)
    throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
    throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
    throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
    throws SQLException;

  /**
   * 关闭 {@link Statement}对象
   *
   * @param statement
   */
  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * 设置 {@link Statement}对象的超时时间
   * Apply a transaction timeout.
   *
   * @param statement a current statement
   * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   * @since 3.4.0
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  /**
   * 针对存储过程调用的处理，其功能是:在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参(parameter)对象中。
   *
   * @param ms
   * @param key
   * @param parameter
   * @param boundSql
   */
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {  // 判断是否为存储过程
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            // 用户传入的实参中设置缓存参数值
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  /**
   * 调用{@link BaseExecutor#doQuery}方法完成数据库查询，并得到映射后的结果对象，{@link BaseExecutor#doQuery}方法是一个抽象方法，
   * 也是4个基本方法之一，由BaseExecutor的子类具体实现。<br>
   * <p>
   * 完全加载：{@link BaseExecutor#doQuery}方法查询数据库之前，会先在{@link BaseExecutor#localCache} 中添加占位符，待查询完成之后，
   * 才将真正的结果对象放到{@link BaseExecutor#localCache}中缓存，此时该缓存项才算“完全加载”。
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // 在缓存中添加占位符
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      // 抽象方法，完成数据库的查询操作，并返回结果对象
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      // 移除旧缓存，这一步我觉得多余，因为下一步骤中会覆盖该key的缓存
      localCache.removeObject(key);
    }
    // 将查询的数据保存到一级缓存
    localCache.putObject(key, list);
    // 如果是储存过程调用，则缓存参数
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  /**
   * 获取数据库连接
   *
   * @param statementLog
   * @return
   * @throws SQLException
   */
  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  /**
   * 负责从 {@link BaseExecutor#localCache}缓存中延迟加载结果对象并设置到 {@link DeferredLoad#resultObject}封装对象的相应属性中
   */
  private static class DeferredLoad {

    /**
     * 外层对象对应的 {@link MetaObject}对象
     */
    private final MetaObject resultObject;
    /**
     * 延迟加载的属性名称
     */
    private final String property;
    /**
     * 延迟加载的属性的类型
     */
    private final Class<?> targetType;
    /**
     * 延迟加载的结果对象在一级缓存中相应的 {@link CacheKey}对象
     */
    private final CacheKey key;
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    /**
     * 负责将结果对象的类型转换
     */
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    /**
     * 检测缓存项是否已经“完全加载”到了缓存中
     *
     * @return
     */
    public boolean canLoad() {
      return localCache.getObject(key) != null && // 检测缓存中是否存在指定的结果对象
        localCache.getObject(key) != EXECUTION_PLACEHOLDER; // 检测是否为占位符
    }

    /**
     * 从缓存中获取数据并转换成 {@link org.apache.ibatis.executor.BaseExecutor.DeferredLoad#targetType}指定的类型再设置到外层结果对象对应的属性中
     */
    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
        // 从缓存中获取结果数据
        List<Object> list = (List<Object>) localCache.getObject(key);
      // 将结果对象转换成targetType指定类型的数据
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      // 将数据设置到外层对象对应的属性中
      resultObject.setValue(property, value);
    }

  }

}
