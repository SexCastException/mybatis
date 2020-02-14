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
package org.apache.ibatis.executor.resultset;

import org.apache.ibatis.annotations.AutomapConstructor;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.cursor.defaults.DefaultCursor;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Iwao AVE!
 * @author Kazuki Shimizu
 */
public class DefaultResultSetHandler implements ResultSetHandler {

  private static final Object DEFERRED = new Object();

  private final Executor executor;
  private final Configuration configuration;
  private final MappedStatement mappedStatement;
  private final RowBounds rowBounds;
  private final ParameterHandler parameterHandler;

  /**
   * 用户指定用于处理结果集的对象
   */
  private final ResultHandler<?> resultHandler;
  private final BoundSql boundSql;
  private final TypeHandlerRegistry typeHandlerRegistry;
  private final ObjectFactory objectFactory;
  private final ReflectorFactory reflectorFactory;

  // nested resultmaps
  /**
   * 嵌套结果对象 <br>
   * 在处理嵌套映射过程中生成的所有结果对象(包括嵌套映射生成的对象)，都会生成相应的 {@link CacheKey}并保存到该集合中。<br>
   */
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<>();
  private final Map<String, Object> ancestorObjects = new HashMap<>();
  private Object previousRowValue;

  // multiple resultsets
  /**
   * resultSet属性值指定的多个结果集对应的 {@link ResultMapping}对象集合，key 为ResultSet的名称
   */
  private final Map<String, ResultMapping> nextResultMaps = new HashMap<>();
  private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<>();

  // Cached Automappings
  private final Map<String, List<UnMappedColumnAutoMapping>> autoMappingsCache = new HashMap<>();

  // temporary marking flag that indicate using constructor mapping (use field to reduce memory usage)
  /**
   * 标志是否使用了构造器映射
   */
  private boolean useConstructorMappings;

  /**
   *
   */
  private static class PendingRelation {
    public MetaObject metaObject;
    public ResultMapping propertyMapping;
  }

  private static class UnMappedColumnAutoMapping {
    /**
     * 未映射的列名
     */
    private final String column;
    /**
     * 未映射列名对应的java属性
     */
    private final String property;
    private final TypeHandler<?> typeHandler;
    /**
     * 是否为基本数据类型
     */
    private final boolean primitive;

    public UnMappedColumnAutoMapping(String column, String property, TypeHandler<?> typeHandler, boolean primitive) {
      this.column = column;
      this.property = property;
      this.typeHandler = typeHandler;
      this.primitive = primitive;
    }
  }

  public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler<?> resultHandler, BoundSql boundSql,
                                 RowBounds rowBounds) {
    this.executor = executor;
    this.configuration = mappedStatement.getConfiguration();
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.parameterHandler = parameterHandler;
    this.boundSql = boundSql;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.reflectorFactory = configuration.getReflectorFactory();
    this.resultHandler = resultHandler;
  }

  //
  // HANDLE OUTPUT PARAMETER
  //

  /**
   * 处理存储过程输出参数
   *
   * @param cs
   * @throws SQLException
   */
  @Override
  public void handleOutputParameters(CallableStatement cs) throws SQLException {
    // 获取用户传入的实际参数，并封装到 MetaObject对象中
    final Object parameterObject = parameterHandler.getParameterObject();
    final MetaObject metaParam = configuration.newMetaObject(parameterObject);
    // 获取BoundSq1.parameterMappings集合，其中记录了参数相关信息
    final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 遍历所有参数信息
    for (int i = 0; i < parameterMappings.size(); i++) {
      final ParameterMapping parameterMapping = parameterMappings.get(i);
      // 如果存在输出类型的参数，则解析参数值，并设置到 parameterObject 中
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        // 如果指定该输出参数为ResultSet类型，则需要进行映射
        if (ResultSet.class.equals(parameterMapping.getJavaType())) {
          // 映射
          handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
        } else {
          // 解析参数值并保存到 parameterObject对象中
          final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
          metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
        }
      }
    }
  }

  /**
   * 负责处理 {@link ResultSet} 类型的输出参数，它会按照指定的 {@link ResultMap} 对该 {@link ResultSet}类型的输出参数进行映射，
   * 并将映射得到 {@link MetaObject}封装的原始对象中
   *
   * @param rs
   * @param parameterMapping
   * @param metaParam
   * @throws SQLException
   */
  private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam) throws SQLException {
    if (rs == null) {
      return;
    }
    try {
      // 获取使用的ResultMap对象
      final String resultMapId = parameterMapping.getResultMapId();
      final ResultMap resultMap = configuration.getResultMap(resultMapId);
      // 获取封装ResultSet的包装类
      final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
      if (this.resultHandler == null) {
        final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
        // 映射并将结果保存到resultHandler 的list集合中
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
        metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
      } else {
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rs);
    }
  }

  //
  // HANDLE RESULT SETS
  //

  /**
   * 不仅可以处理 {@link Statement}、 {@link PreparedStatement} 产生的结果集，还可以处理 {@link CallableStatement}调用
   * 存储过程产生的多结果集。
   * <p>
   * 例如下面定义的test_proc_multi_result_set存储过程产生的多结果集
   * <p>
   * CREATE PROCEDURE test_proc_multi_result_set()
   * BEGIN
   * select * from person;
   * select * from item;
   * END;
   *
   * @param stmt
   * @return
   * @throws SQLException
   */
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());

    // 保存映射结果集得到的多个结果对象
    final List<Object> multipleResults = new ArrayList<>();

    int resultSetCount = 0;
    // 获取第一个ResultSet对象，正如前面所说，可能存在多个ResultSet，这里只获取第一个ResultSet
    ResultSetWrapper rsw = getFirstResultSet(stmt);

    /*
        获取MappedStatement.resultMaps集合，映射文件中的<resultMap>节点会被解析成ResultMap对象（存在<collection>和<association>
        或他们之间的嵌套都会被解析成 ResultMap对象），保存到MappedStatement.resultMaps集合中
        如果SQL节点能够产生多个ResultSet，,那么我们可以在SQL节点的resultMap属性中配置多个<resultMap>节点的id，
        它们之间通过", "分隔，实现对多个结果集的映射
    */
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    int resultMapCount = resultMaps.size();
    // 校验数据
    validateResultMapsCount(rsw, resultMapCount);
    // 遍历ResultMap集合
    while (rsw != null && resultMapCount > resultSetCount) {
      //
      ResultMap resultMap = resultMaps.get(resultSetCount);
      /*
          根据ResultMap中定义的映射规则对ResultSet进行映射，并将映射的结果对象添加到 multipleResults 集合中保存
      */
      handleResultSet(rsw, resultMap, multipleResults, null);
      rsw = getNextResultSet(stmt);
      // 清空nestedResultObjects集合
      cleanUpAfterHandlingResultSet();
      // 累加，处理下一个结果集
      resultSetCount++;
    }

    /*
        获取MappedStatement.resultSets属性。该属性仅对多结果集的情况适用，该属性将列出语句执
        行后返回的结果集，并给每个结果集一个名称，名称是逗号分隔的，然后根据ResultSet的名称进行嵌套映射
    */
    String[] resultSets = mappedStatement.getResultSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {
        // 根据resultSet的名称，获取未处理的ResultMapping
        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          // 获取多结果集ResultMap嵌套的ResultMap对象
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          // 根据ResultMap对象映射结果集，多结果集映射，第三个参数为空，因为映射的结果直接保存设置到外层对象的相应属性中
          handleResultSet(rsw, resultMap, null, parentMapping);
        }
        // 处理下一个结果集
        rsw = getNextResultSet(stmt);
        // 清空nestedResultObjects集合
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    return collapseSingleResultList(multipleResults);
  }

  @Override
  public <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling cursor results").object(mappedStatement.getId());

    // 获取结果集并封装成ResultSetWrapper对象
    ResultSetWrapper rsw = getFirstResultSet(stmt);

    // 获取映射使用的ResultMap对象集合
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();

    int resultMapCount = resultMaps.size();
    validateResultMapsCount(rsw, resultMapCount);
    if (resultMapCount != 1) {
      throw new ExecutorException("Cursor results cannot be mapped to multiple resultMaps");
    }

    // 使用第一个结果集
    ResultMap resultMap = resultMaps.get(0);
    // 将ResultSetWrapper对象、映射使用的ResultMap对象以及控制映射的起止位置的RowBounds对象封装成DefaultCursor对象
    return new DefaultCursor<>(this, resultMap, rsw, rowBounds);
  }

  private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
    // 获取结果集
    ResultSet rs = stmt.getResultSet();
    while (rs == null) {
      // move forward to get the first resultset in case the driver
      // doesn't return the resultset as the first result (HSQLDB 2.1)
      /*
          如果驱动程序没有将结果集作为第一个结果返回，则继续前进以获取第一个结果集（HSQLDB 2.1）
      */
      // 检测时候有代理处理的ResultSet
      if (stmt.getMoreResults()) {
        rs = stmt.getResultSet();
      } else {
        if (stmt.getUpdateCount() == -1) {  // 没有待处理的ResultSet
          // no more results. Must be no resultset
          break;
        }
      }
    }
    // 将结果集封装成ResultSetWrapper对象
    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
  }

  /**
   * 获取下一个结果集 {@link ResultSet}对象，并封装到 {@link ResultSetWrapper}返回
   *
   * @param stmt
   * @return
   */
  private ResultSetWrapper getNextResultSet(Statement stmt) {
    // Making this method tolerant of bad JDBC drivers
    try {
      // 检测JDBC是否支持多个结果集
      if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
        // Crazy Standard JDBC way of determining if there are more results
        // 检测是否还有待处理的结果集，若存在，则封装成ResultSetWrapper对象并返回
        if (!(!stmt.getMoreResults() && stmt.getUpdateCount() == -1)) {
          ResultSet rs = stmt.getResultSet();
          if (rs == null) {
            // 获取不到ResultSet则迭代获取，直至没有更多的结果集为止
            return getNextResultSet(stmt);
          } else {
            return new ResultSetWrapper(rs, configuration);
          }
        }
      }
    } catch (Exception e) {
      // Intentionally ignored.
    }
    return null;
  }

  /**
   * 关闭结果集
   *
   * @param rs
   */
  private void closeResultSet(ResultSet rs) {
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    }
  }

  /**
   * 清空 {@link DefaultResultSetHandler#nestedResultObjects}集合
   */
  private void cleanUpAfterHandlingResultSet() {
    nestedResultObjects.clear();
  }

  /**
   * 校验ResultMap集合的个数，如果结果集不为空，则resultMaps集合不能为空，否则抛出异常
   *
   * @param rsw
   * @param resultMapCount
   */
  private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
  }

  /**
   * 处理结果，核心的方法 {@link DefaultResultSetHandler#handleRowValues}
   * <p>
   * parentMapping不为null，则为内层结果集映射，否则为外层结果集映射
   *
   * @param rsw
   * @param resultMap
   * @param multipleResults
   * @param parentMapping
   * @throws SQLException
   */
  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults, ResultMapping parentMapping) throws SQLException {
    try {
      if (parentMapping != null) {  // 内层结果集映射
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      } else {  // 外层结果集映射
        if (resultHandler == null) {
          // 未指定处理映射结果对象的ResultHandler对象，则使用DefaultResultHandler作为默认的ResultHandler对象
          DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
          // 对ResultSet进行映射,并将映射得到的结果对象添加到DefaultResultHandler对象中暂存
          handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
          // 将DefaultResultHandler中保存的结果对象添加到multipleResults集合中
          multipleResults.add(defaultResultHandler.getResultList());
        } else {
          // 使用用户指定的ResultHandler对象处理结果对象
          handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
        }
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rsw.getResultSet());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> collapseSingleResultList(List<Object> multipleResults) {
    return multipleResults.size() == 1 ? (List<Object>) multipleResults.get(0) : multipleResults;
  }

  //
  // HANDLE ROWS FOR SIMPLE RESULTMAP
  //

  /**
   * 该方法是映射结果集的核心代码，其中有两个分支:
   * 一、针对包含嵌套映射的处理
   * 二、针对不含嵌套映射的简单映射的处理。
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   * @throws SQLException
   */
  public void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    // 针对存在嵌套ResultMap的情况
    if (resultMap.hasNestedResultMaps()) {
      // 检测是否允许在嵌套中使用RowBounds
      ensureNoRowBounds();
      // 检测是否允许在嵌套映射中使用用户自定义的ResultHandler
      checkResultHandler();
      // 含嵌套映射的多结果处理
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    } else {
      // 不含嵌套映射的简单映射处理
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }

  /**
   * 检测是否允许在嵌套中使用 {@link RowBounds}
   */
  private void ensureNoRowBounds() {
    if (configuration.isSafeRowBoundsEnabled() && rowBounds != null && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
        + "Use safeRowBoundsEnabled=false setting to bypass this check.");
    }
  }

  /**
   * 检测是否允许在嵌套映射中使用用户自定义的 {@link ResultHandler}
   */
  protected void checkResultHandler() {
    if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
        + "Use safeResultHandlerEnabled=false setting to bypass this check "
        + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  }

  /**
   * 处理非嵌套简单结果集映射 <br>
   * 大致步骤如下:<br>
   * (1)调用 {@link DefaultResultSetHandler#skipRows}方法，根据 {@link RowBounds#offset} 值定位到指定的记录行。<br>
   * (2)调用 {@link DefaultResultSetHandler#shouldProcessMoreRows}，检测是否还有需要映射的记录。<br>
   * (3)通过 {@link DefaultResultSetHandler#resolveDiscriminatedResultMap}方法，确定映射使用的 {@link ResultMap}对象。<br>
   * (4)调用 {@link DefaultResultSetHandler#getRowValue(ResultSetWrapper, ResultMap, String)}方法对 {@link ResultSet} 中的一行记录进行映射:<br>
   * <div style="text-indent: 12px;">(a)通过{@link DefaultResultSetHandler#createResultObject}方法创建映射后的结果对象。</div><br>
   * <div style="text-indent: 12px;">(b)通过{@link DefaultResultSetHandler#shouldApplyAutomaticMappings}方法判断是否开启了自动映射功能。</div><br>
   * <div style="text-indent: 12px;">(c)通过{@link DefaultResultSetHandler#applyAutomaticMappings}方法自动映射ResultMap中未明确映射的列。</div><br>
   * <div style="text-indent: 12px;">(d) 通过{@link DefaultResultSetHandler#applyPropertyMappings}方法映射ResultMap中明确映射列，到这里该行记录的数据已经完全映射到了结果对象的相应属性中。</div><br>
   * (5)调用{@link DefaultResultSetHandler#storeObject}方法保存映射得到的结果对象。<br>
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   * @throws SQLException
   */
  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
    throws SQLException {
    // 默认结果上下文对象
    DefaultResultContext<Object> resultContext = new DefaultResultContext<>();
    ResultSet resultSet = rsw.getResultSet();
    // 步骤1：根据RowBounds中的offset定位到指定的记录
    skipRows(resultSet, rowBounds);
    // 步骤2：检测已经处理的行数是否已经达到上限（RowBounds.limit）以及ResultSet中是否还有可处理的对象
    while (shouldProcessMoreRows(resultContext, rowBounds) && !resultSet.isClosed() && resultSet.next()) {
      // 步骤3：根据该行记录以及ResultMap.discriminator，决定映射使用后的结果对象
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(resultSet, resultMap, null);
      // 步骤4：根据最终确定的ResultMap对ResultSet中的该行记录进行映射，得到映射后的结果对象
      // rowValue为每行记录映射的对象
      Object rowValue = getRowValue(rsw, discriminatedResultMap, null);
      // 步骤5：将映射创建的结果对象添加到ResultHandler.resultList中保存
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
    }
  }

  /**
   * 保存映射得到的结果对象 <br>
   * <p>
   * 如果多结果映射，则保存到父对象对应的属性中;
   * 如果是普通映射(最外层映射或是非嵌套的简单映射)的结果对象，则保存到 {@link ResultHandler}中。
   *
   * @param resultHandler
   * @param resultContext
   * @param rowValue
   * @param parentMapping
   * @param rs
   * @throws SQLException
   */
  private void storeObject(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue, ResultMapping parentMapping, ResultSet rs) throws SQLException {
    if (parentMapping != null) {
      // 多结果映射属性关联
      linkToParents(rs, parentMapping, rowValue);
    } else {
      // 普通映射，将结果保存到ResultHandler中
      callResultHandler(resultHandler, resultContext, rowValue);
    }
  }

  @SuppressWarnings("unchecked" /* because ResultHandler<?> is always ResultHandler<Object>*/)
  private void callResultHandler(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue) {
    /*
        递增 DefaultResultContext.resultCount，该值用于检测处理的记录行数是否已经达到上限(在RowBounds.limit字段中记录了该上限)。
        之后将结果对象保存到 DefaultResultContext.resultObject 字段中
    */
    resultContext.nextResultObject(rowValue);
    // 将结果对象添加到 ResultHandler.resultList中保存
    ((ResultHandler<Object>) resultHandler).handleResult(resultContext);
  }

  /**
   * 检测已经处理的行数是否已经达到上限（{@link RowBounds#limit}）以及ResultSet中是否还有可处理的对象
   *
   * @param context
   * @param rowBounds
   * @return
   */
  private boolean shouldProcessMoreRows(ResultContext<?> context, RowBounds rowBounds) {
    return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
  }

  /**
   * 根据 {@link RowBounds#offset}字段的值定位到指定的记录，可以实现分页效果
   *
   * @param rs
   * @param rowBounds
   * @throws SQLException
   */
  private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
    // 根据ResultSet的类型进行定位
    if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
      if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
        // 直接定位到offset指定的记录
        rs.absolute(rowBounds.getOffset());
      }
    } else {
      // 通过调用ResultSet.next()方法移动到指定的记录
      for (int i = 0; i < rowBounds.getOffset(); i++) {
        if (!rs.next()) {
          break;
        }
      }
    }
  }

  //
  // GET VALUE FROM ROW FOR SIMPLE RESULT MAP
  //

  /**
   * 创建结果对象并完成结果对象的映射，基本步骤如下:<br/>
   * (1)根据 {@link ResultMap}指定的类型创建对应的结果对象（如果配置了构造器映射，则创建对象的时映射部分值），以及对应的 {@link MetaObject}对象。<br/>
   * (2)根据配置信息，决定是否自动映射{@link ResultMap}中未明确映射的列。<br/>
   * (3)根据{@link ResultMap}映射明确指定的属性和列。<br/>
   * (4)返回映射得到的结果对象。<br/>
   *
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    final ResultLoaderMap lazyLoader = new ResultLoaderMap();
    // 步骤1:创建该行记录映射之后得到的结果对象，该结果对象的类型由<resultMap>节点的type属性指定
    Object rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
    // 判断结果对象是否有对应的TypeHandler对象，存在一般是系统内置的或者已经自定义，如Integer和Date等等处理单列的TypeHandler对象
    if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) { // 不存在才映射
      // 创建保存结果对象元数据对象的MetaObject对象
      final MetaObject metaObject = configuration.newMetaObject(rowValue);
      // 成功映射任意属性，则foundValues为true;否则foundValues为false
      boolean foundValues = this.useConstructorMappings;
      // 判断是否开启了自动映射功能
      if (shouldApplyAutomaticMappings(resultMap, false)) {
        // 步骤2:自动映射ResultMap中未明确指定的列
        foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
      }
      // 步骤3:映射ResultMap中明确指定需要映射的列
      foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
      foundValues = lazyLoader.size() > 0 || foundValues;
      // 步骤4:如果没有成功映射任何属性，则根据Configuration对象的returnInstanceForEmptyRow属性决定是返回空的结果对象还是null
      rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
    }
    return rowValue;
  }

  /**
   * 检测是否开启了自动映射功能，该功能会自动映射结果集中存在的，但未在 {@link ResultMap}中明确的列 <br>
   * <p>
   * 分以下两种情况：
   * 一、在ResultMap中明确地配置了autoMapping属性，则优先根据该属性的值决定是否开启自动映射功能。<br>
   * 二、如果没有配置autoMapping属性，则在根据mybatis-config.xml中<settings>节点中配置的autoMappingBehavior 值( 默认为PARTIAL)
   * 决定是否开启自动映射功能。
   *
   * @param resultMap
   * @param isNested  是否考虑嵌套映射的情况
   * @return
   */
  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
    if (resultMap.getAutoMapping() != null) {
      return resultMap.getAutoMapping();
    } else {
      if (isNested) {
        return AutoMappingBehavior.FULL == configuration.getAutoMappingBehavior();
      } else {
        return AutoMappingBehavior.NONE != configuration.getAutoMappingBehavior();
      }
    }
  }

  //
  // PROPERTY MAPPINGS
  //

  /**
   * 处理 {@link ResultMap}中明确需要进行映射的列，在该方法中涉及延迟加载、嵌套映射等内容。
   *
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {
    // 获取该ResultMap中明确需要进行映射的列名集合
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
    // 标志是否映射成功，只要有一项成功映射，则为true
    boolean foundValues = false;
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
    for (ResultMapping propertyMapping : propertyMappings) {
      // 处理列前缀
      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      // 如果用户配置了嵌套映射，则忽略配置的column属性值
      if (propertyMapping.getNestedResultMapId() != null) {
        // the user added a column attribute to a nested result map, ignore it
        column = null;
      }
      /*
          下面的逻辑主要处理三种场景
          场景1: column 是"{prop1=col1, prop2=co12}"这种形式的，一般与嵌套查询配合使用, 表示将coll和co12的列值传递给内层嵌套查询作为参数
          场景2:基本类型的属性映射
          场景3:多结果集的场景处理，该属性来自另一个结果集
       */
      if (propertyMapping.isCompositeResult() // 场景1
        || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) // 场景2
        || propertyMapping.getResultSet() != null) {  // 场景3
        // 获取映射到该属性的结果集
        Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);
        // issue #541 make property optional
        // 获取映射的属性名
        final String property = propertyMapping.getProperty();
        if (property == null) {
          continue;
        } else if (value == DEFERRED) {
          foundValues = true;
          continue;
        }
        if (value != null) {
          foundValues = true;
        }
        if (value != null || (configuration.isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive())) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          // 将映射的结果赋值到外层对象中
          metaObject.setValue(property, value);
        }
      }
    }
    return foundValues;
  }

  /**
   * 获取 {@link ResultMapping}的property属性相应的映射列值
   *
   * @param rs
   * @param metaResultObject
   * @param propertyMapping
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {
    if (propertyMapping.getNestedQueryId() != null) { // 嵌套查询
      return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
    } else if (propertyMapping.getResultSet() != null) {  // 多结果处理
      addPendingChildRelation(rs, metaResultObject, propertyMapping);   // TODO is that OK?
      return DEFERRED;
    } else {  // 普通列值的映射
      // 获取响应的类型处理器
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      // 拼接完整的列名
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      // 从结果集映射该列的值并返回
      return typeHandler.getResult(rs, column);
    }
  }

  /**
   * 负责为未明确映射的列查找对应的属性，并将两者（列和属性）关联起来封装成 {@link UnMappedColumnAutoMapping}对象。<br>
   * 该方法产生的 {@link UnMappedColumnAutoMapping} 对象集合会缓存在 {@link DefaultResultSetHandler#autoMappingsCache}字段中，
   * 其中的key由 {@link ResultMap}的id与列前缀构成。<br>
   *
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private List<UnMappedColumnAutoMapping> createAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    // 拼接自动映射缓存key
    final String mapKey = resultMap.getId() + ":" + columnPrefix;
    List<UnMappedColumnAutoMapping> autoMapping = autoMappingsCache.get(mapKey);
    if (autoMapping == null) {
      autoMapping = new ArrayList<>();
      // 获取未映射列名集合
      final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
      for (String columnName : unmappedColumnNames) {
        String propertyName = columnName;
        // 如果配置了列前缀，则去掉列前缀的部分作为属性名称
        if (columnPrefix != null && !columnPrefix.isEmpty()) {
          // When columnPrefix is specified,
          // ignore columns without the prefix.
          // 如果以列前缀开头，则截掉列前缀
          if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
            propertyName = columnName.substring(columnPrefix.length());
          } else {  // 配置了列前缀却不是以列前缀开头，则注定配置的列名和属性名是不匹配，注定映射失败的
            continue;
          }
        }
        final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
        // 判断MetaObject封装的元对象中是否有该属性名称并且提供了setter方法
        if (property != null && metaObject.hasSetter(property)) {
          // 校验，在明确映射的列中如果有该属性，则忽略以下步骤
          if (resultMap.getMappedProperties().contains(property)) {
            continue;
          }
          // 获取该java属性提供的setter方法形参类型
          final Class<?> propertyType = metaObject.getSetterType(property);
          if (typeHandlerRegistry.hasTypeHandler(propertyType, rsw.getJdbcType(columnName))) {
            // 获取对应的TypeHandler 对象
            final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
            autoMapping.add(new UnMappedColumnAutoMapping(columnName, property, typeHandler, propertyType.isPrimitive()));
          } else {
            configuration.getAutoMappingUnknownColumnBehavior()
              .doAction(mappedStatement, columnName, property, propertyType);
          }
        } else {  // 配置了列前缀，但去掉列前缀的部分与metaObject封装的元对象没有匹配的属性或者有匹配的属性，但未提供有该属性的setter方法
          configuration.getAutoMappingUnknownColumnBehavior()
            .doAction(mappedStatement, columnName, (property != null) ? property : propertyName, null);
        }
      }
      autoMappingsCache.put(mapKey, autoMapping);
    }
    return autoMapping;
  }

  /**
   * 自动映射ResultMap中未明确的列 <br>
   * 未明确列：指在 {@link ResultSet}中存在的列名或别名，但在ResultMap中没有明确指定要映射这些列名且结果对象中存在与该列名相匹配的属性
   *
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    /*
        获取在ResultSet中存在，但ResultMap中没有明确映射的列所对应的UnMappedColumnAutoMapping集合，
        如果ResultMap中设置的resultType为java.util.HashMap的话，则全部的列都会在这里获取到
    */
    List<UnMappedColumnAutoMapping> autoMapping = createAutomaticMappings(rsw, resultMap, metaObject, columnPrefix);
    // 标志是否映射成功，只要有一项成功映射，则为true
    boolean foundValues = false;
    if (!autoMapping.isEmpty()) {
      // 遍历集合
      for (UnMappedColumnAutoMapping mapping : autoMapping) {
        // 映射相应的值
        final Object value = mapping.typeHandler.getResult(rsw.getResultSet(), mapping.column);
        if (value != null) {
          foundValues = true;
        }
        if (value != null || (configuration.isCallSettersOnNulls() && !mapping.primitive)) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          // 将映射的值赋值到对应列
          metaObject.setValue(mapping.property, value);
        }
      }
    }
    return foundValues;
  }

  // MULTIPLE RESULT SETS

  /**
   * 将映射的结果设置到外层对象
   *
   * @param rs
   * @param parentMapping
   * @param rowValue
   * @throws SQLException
   */
  private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {
    // 为多结果集映射创建CacheKey对象，注意最后一个参数使用的是 foreignColumn属性值
    CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());
    List<PendingRelation> parents = pendingRelations.get(parentKey);
    if (parents != null) {
      for (PendingRelation parent : parents) {
        if (parent != null && rowValue != null) {
          // 将当前记录的结果对象添加到外层对象的相应属性中
          linkObjects(parent.metaObject, parent.propertyMapping, rowValue);
        }
      }
    }
  }

  private void addPendingChildRelation(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping) throws SQLException {
    // 创建多结果集映射的CacheKey对象
    CacheKey cacheKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getColumn());
    // 创建PendingRelation,记录当前结果对象相应的 MetaObject和 ResultMapping
    PendingRelation deferLoad = new PendingRelation();
    deferLoad.metaObject = metaResultObject;
    deferLoad.propertyMapping = parentMapping;
    List<PendingRelation> relations = pendingRelations.computeIfAbsent(cacheKey, k -> new ArrayList<>());
    // issue #255
    // 将deferLoad对象缓存到 pendingRelations
    relations.add(deferLoad);
    // 获取待处理的另一个结果集的ResultMapping
    ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet());
    if (previous == null) {
      // 把多结果集映射的ResultMapping加入到nextResultMaps，以便在 handleResultSets 处理多结果集映射
      nextResultMaps.put(parentMapping.getResultSet(), parentMapping);
    } else {
      // 如果同名的结果集对应不同的ResultMapping对象，则抛出异常
      if (!previous.equals(parentMapping)) {
        throw new ExecutorException("Two different properties are mapped to the same resultSet");
      }
    }
  }

  /**
   * 为多结果集创建 {@link CacheKey}对象
   *
   * @param rs
   * @param resultMapping
   * @param names
   * @param columns
   * @return
   * @throws SQLException
   */
  private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns) throws SQLException {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMapping);
    if (columns != null && names != null) {
      // 多列用“,”隔开
      String[] columnsArray = columns.split(",");
      String[] namesArray = names.split(",");
      for (int i = 0; i < columnsArray.length; i++) {
        // 遍历映射每一列的值
        Object value = rs.getString(columnsArray[i]);
        if (value != null) {
          // 将需要映射的属性和映射的值保存到CacheKey的updateList集合
          cacheKey.update(namesArray[i]);
          cacheKey.update(value);
        }
      }
    }
    return cacheKey;
  }

  //
  // INSTANTIATION & CONSTRUCTOR MAPPING
  //

  /**
   * 创建需要映射的结果对象，可能返回null，如果存在延迟记载，则返回结果类型的代理对象，并将结果对象的已映射的属性值赋值给代理对象
   *
   * @param rsw
   * @param resultMap
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    this.useConstructorMappings = false; // reset previous mapping result
    // 保存使用重载函数createResultObject()创建对象使用的构造函数形参类型列表，此集合非空，则表明使用了构造器映射
    final List<Class<?>> constructorArgTypes = new ArrayList<>();
    // 保存使用重载函数createResultObject()创建对象使用的构造函数实参值（数据库列值）列表
    final List<Object> constructorArgs = new ArrayList<>();
    // 创建结果对象，可能构造器映射初始化了部分属性
    Object resultObject = createResultObject(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
    // 判断结果对象是否有对应的TypeHandler对象
    if (resultObject != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
      // 获取配置有property属性值的ResultMapping对象，一般是<id>、<result>、<association>和<collection>节点配置生成的ResultMapping对象
      final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
      for (ResultMapping propertyMapping : propertyMappings) {
        // issue gcode #109 && issue #149
        // 如果存在嵌套查询且该属性为延迟加载的属性，则使用ProxyFactory 创建代理对象，
        // 默认使用的是 JavassistProxyFactory
        // 没有select属性嵌套查询并且为懒加载，一般是<association>和<collection>节点配置生成的ResultMapping对象
        if (propertyMapping.getNestedQueryId() != null && propertyMapping.isLazy()) {
          // 创建懒加载代理对象
          resultObject = configuration.getProxyFactory().createProxy(resultObject, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
          break;
        }
      }
    }
    // constructorArgTypes非空，则表明使用了构造器映射
    this.useConstructorMappings = resultObject != null && !constructorArgTypes.isEmpty(); // set current mapping result
    return resultObject;
  }

  /**
   * 创建需要映射的结果对象，重载方法，创建对象过程中，如果存在构造器映射，则先使用映射值作为构造器实参再通过构造器实例化对象<br>
   * 分为下面4种场景 <br>
   * 场景1：结果集只有一列，且存在可以处理该列的 {@link TypeHandler}对象，一般为基本类型、String、Date等映射 <br>
   * 场景2：{@link ResultMap}中记录了<constructor>节点的信息，则通过反射方式调用构造方法，创建结果对象 <br>
   * 场景3：存在默认的无参构造函数，则直接使用 {@link ObjectFactory}创建对象，即不存在任何映射，返回空数据对象 <br>
   * 场景4：如果开启了自动映射，则通过自动映射的方式查找合适的构造方法并创建结果对象 <br>
   * <p>
   * 场景1使用 {@link TypeHandler}完成单列的结果映射
   *
   * @param rsw
   * @param resultMap
   * @param constructorArgTypes
   * @param constructorArgs
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
    throws SQLException {
    // 获取ResultMap中记录的type属性，也就是该行记录最终映射成的结果对象类型
    final Class<?> resultType = resultMap.getType();
    // 创建该类型对应的MetaClass对象
    final MetaClass metaType = MetaClass.forClass(resultType, reflectorFactory);
    // 获取ResultMap中记录的<constructor>节点信息，如果该集合不为空，则可以通过该集合确定相应Java类中的唯一构造函数
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();

    if (hasTypeHandlerForResultObject(rsw, resultType)) { // 场景1
      return createPrimitiveResultObject(rsw, resultMap, columnPrefix);
    } else if (!constructorMappings.isEmpty()) {  // 场景2
      return createParameterizedResultObject(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
    } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {  // 场景3
      return objectFactory.create(resultType);
    } else if (shouldApplyAutomaticMappings(resultMap, false)) {  // 场景4
      return createByConstructorSignature(rsw, resultType, constructorArgTypes, constructorArgs);
    }
    // 初始化失败，抛出异常
    throw new ExecutorException("Do not know how to create an instance of " + resultType);
  }

  /**
   * 根据<constructor>节点的配置，根据constructorArgTypes和constructorArgs选择合适的构造方法创建结果对象，
   * 其中也会涉及嵌套查询和嵌套映射的处理。
   *
   * @param rsw
   * @param resultType
   * @param constructorMappings <constructor>节点解析的 {@link ResultMapping}对象
   * @param constructorArgTypes 记录构造函数参数类型
   * @param constructorArgs     记录构造函数实参
   * @param columnPrefix
   * @return
   */
  Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType, List<ResultMapping> constructorMappings,
                                         List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) {
    boolean foundValues = false;
    // 遍历<constructor>节点解析出来的ResultMapping对象
    for (ResultMapping constructorMapping : constructorMappings) {
      // 获取当前构造函数形参的类型，即<constructor>子节点的javaType属性值
      final Class<?> parameterType = constructorMapping.getJavaType();
      final String column = constructorMapping.getColumn();
      // 保存实参值
      final Object value;
      try {
        // 处理<constructor>子节点select属性指定的查询语句
        if (constructorMapping.getNestedQueryId() != null) {
          // 嵌套处理，通过嵌套查询得到实参
          value = getNestedQueryConstructorValue(rsw.getResultSet(), constructorMapping, columnPrefix);
        } else if (constructorMapping.getNestedResultMapId() != null) { // 处理<constructor>子节点resultMap属性指定的resultMap
          // 嵌套+递归处理，通过嵌套映射得到实参
          final ResultMap resultMap = configuration.getResultMap(constructorMapping.getNestedResultMapId());
          value = getRowValue(rsw, resultMap, getColumnPrefix(columnPrefix, constructorMapping));
        } else {
          // 直接获取该列的值，然后经过TypeHandler对象的转换，得到构造函数的实参
          final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
          value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
        }
      } catch (ResultMapException | SQLException e) {
        throw new ExecutorException("Could not process result for mapping: " + constructorMapping, e);
      }
      // 保存构造函数形参类型
      constructorArgTypes.add(parameterType);
      // 将数据库映射的值作为构造器实参
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    // 调用匹配的构造函数并创建结果
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  /**
   * 选择合适的构造器并将映射的值作为构造器实参实例化对象
   *
   * @param rsw
   * @param resultType
   * @param constructorArgTypes
   * @param constructorArgs
   * @return
   * @throws SQLException
   */
  private Object createByConstructorSignature(ResultSetWrapper rsw, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) throws SQLException {
    // 获取返回结果类型的所有构造器
    final Constructor<?>[] constructors = resultType.getDeclaredConstructors();
    // 查找合适的构造器
    final Constructor<?> defaultConstructor = findDefaultConstructor(constructors);
    if (defaultConstructor != null) {
      return createUsingConstructor(rsw, resultType, constructorArgTypes, constructorArgs, defaultConstructor);
    } else {  // 没有只带一个形参类型的构造器或带有 @AutomapConstructor注解修改的构造器
      for (Constructor<?> constructor : constructors) {
        // 是否允许构造器使用 TypeHandler 对象
        if (allowedConstructorUsingTypeHandlers(constructor, rsw.getJdbcTypes())) {
          return createUsingConstructor(rsw, resultType, constructorArgTypes, constructorArgs, constructor);
        }
      }
    }
    throw new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rsw.getClassNames());
  }

  /**
   * 只要至少有一项通过构造器形参成功映射，则将映射的值作为实参通过构造器创建对象并返回，否则返回null
   * <p>
   *
   * @param rsw
   * @param resultType
   * @param constructorArgTypes
   * @param constructorArgs
   * @param constructor
   * @return
   * @throws SQLException
   */
  private Object createUsingConstructor(ResultSetWrapper rsw, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor) throws SQLException {
    boolean foundValues = false;
    // 遍历指定构造器的形参类型
    for (int i = 0; i < constructor.getParameterTypes().length; i++) {
      Class<?> parameterType = constructor.getParameterTypes()[i];
      String columnName = rsw.getColumnNames().get(i);
      // 获取相应的TypeHandler对象
      TypeHandler<?> typeHandler = rsw.getTypeHandler(parameterType, columnName);
      // 获取该列值
      Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
      // 记录使用哪个构造器创建对象的形参类型列表
      constructorArgTypes.add(parameterType);
      // 将值作为构造器实参
      constructorArgs.add(value);
      // 只要有一项映射成功即为true
      foundValues = value != null || foundValues;
    }
    // 创建并返回对象
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  /**
   * 优先返回带一个形参的构造器，否则遍历返回带注解 {@link AutomapConstructor}的构造器
   *
   * @param constructors
   * @return
   */
  private Constructor<?> findDefaultConstructor(final Constructor<?>[] constructors) {
    if (constructors.length == 1) {
      return constructors[0];
    }

    for (final Constructor<?> constructor : constructors) {
      if (constructor.isAnnotationPresent(AutomapConstructor.class)) {
        return constructor;
      }
    }
    return null;
  }

  /**
   * 是否允许构造器使用 {@link TypeHandler}对象 <br>
   * <p>
   * 判断构造器每一项形参是否存在对应的TypeHandler对象，只有存在的情况下，使用构造器映射的时候才能将列值作为实参赋给形参
   *
   * @param constructor
   * @param jdbcTypes
   * @return
   */
  private boolean allowedConstructorUsingTypeHandlers(final Constructor<?> constructor, final List<JdbcType> jdbcTypes) {
    // 获取指定构造器参数类型
    final Class<?>[] parameterTypes = constructor.getParameterTypes();
    if (parameterTypes.length != jdbcTypes.size()) {
      return false;
    }
    // 遍历构造器类型，如果存在形参类型没有对应的TypeHandler对象的情况，则返回false
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!typeHandlerRegistry.hasTypeHandler(parameterTypes[i], jdbcTypes.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * 直接返回单列映射的结果
   *
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    final Class<?> resultType = resultMap.getType();
    final String columnName;
    // 如果查询的单列是使用resultMap配置的
    if (!resultMap.getResultMappings().isEmpty()) {
      // 获取配置单列的ResultMapping对象
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      final ResultMapping mapping = resultMappingList.get(0);
      // 使用前缀拼接resultMap配置的单列名
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
    } else {
      // 否则获取查询的列名或别名
      columnName = rsw.getColumnNames().get(0);
    }
    // 获取相应的 TypeHandler对象
    final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);
    // 通过 TypeHandler对象映射相应的值
    return typeHandler.getResult(rsw.getResultSet(), columnName);
  }

  //
  // NESTED QUERY
  //

  /**
   * 通过<constructor>子节点的 {@link ResultMapping}的select属性指定的id找到该构造器对应的形参 <br>
   * 在创建构造函数的参数时涉及的嵌套查询，无论配置如何，都不会延迟加载。
   *
   * @param rs
   * @param constructorMapping
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix) throws SQLException {
    // 通过<constructor>子节点select属性找到相应的MappedStatement对象
    final String nestedQueryId = constructorMapping.getNestedQueryId();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    // 获取查询参数的类型
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, constructorMapping, nestedQueryParameterType, columnPrefix);
    Object value = null;
    if (nestedQueryParameterObject != null) {
      // 获取嵌套查询对应的BoundSql对象和相应的CacheKey对象
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      // 获取嵌套查询结果集经过映射后的目标类型
      final Class<?> targetType = constructorMapping.getJavaType();
      // 创建ResultLoader对象，并调用loadResult() 方法执行嵌套查询，得到相应的构造方法参数值
      final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
      value = resultLoader.loadResult();
    }
    return value;
  }

  private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {
    // 获取嵌套查询的id
    final String nestedQueryId = propertyMapping.getNestedQueryId();
    // 获取需要映射的属性值
    final String property = propertyMapping.getProperty();
    // 通过id获取嵌套查询的MappedStatement对象
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping, nestedQueryParameterType, columnPrefix);
    Object value = null;
    if (nestedQueryParameterObject != null) {
      // 获取嵌套查询对应的BoundSq1对象和相应CacheKey对象
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      // 获取嵌套查询结果集经过映射后的目标类型
      final Class<?> targetType = propertyMapping.getJavaType();
      // 检测缓存中是否存在该嵌套查询的结果对象
      if (executor.isCached(nestedQuery, key)) {
        // 创建DeferredLoad对象，并通过该DeferredLoad对象从缓存中加载结果对象
        executor.deferLoad(nestedQuery, metaResultObject, property, key, targetType);
        // 返回 DEFERRED 标识(是一个特殊的标识对象)
        value = DEFERRED;
      } else {
        // 创建嵌套查询相应的ResultLoader对象
        final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
        /*
            如果该属性配置了延迟加载，则将其添加到ResultLoaderMap中，等待真正使用时再执行嵌套查询并得到结果对象
        */
        if (propertyMapping.isLazy()) {
          lazyLoader.addLoader(property, metaResultObject, resultLoader);
          value = DEFERRED; // 返回DEFERED标识
        } else {
          // 没有配置延迟加载，则直接调用 ResultLoader.loadResult() 方法执行嵌套查询，并映射得到结果对象
          value = resultLoader.loadResult();
        }
      }
    }
    return value;
  }

  /**
   * 映射嵌套查询的实参值
   *
   * @param rs
   * @param resultMapping
   * @param parameterType
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    // column属性值是否指定组合列
    if (resultMapping.isCompositeResult()) {
      return prepareCompositeKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    } else {
      return prepareSimpleKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    }
  }

  /**
   * 通过形参类型或映射的列名获取对应的 {@link TypeHandler}对象，并通过该处理器对象获取该列的值
   *
   * @param rs
   * @param resultMapping
   * @param parameterType
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    final TypeHandler<?> typeHandler;
    // 是否有形参对应的TypeHandler对象
    if (typeHandlerRegistry.hasTypeHandler(parameterType)) {  // 一般为系统内置的处理器或自定义类型处理器处理的类型
      typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
    } else {
      typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
    }
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    // 创建形参对象
    final Object parameterObject = instantiateParameterObject(parameterType);
    // 创建封装形参对象元数据的MetaObject对象
    final MetaObject metaObject = configuration.newMetaObject(parameterObject);
    boolean foundValues = false;
    // 遍历复合列
    for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
      // 获取该列类型
      final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty());
      // 获取该列typeHandler对象
      final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);
      final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn(), columnPrefix));
      // issue #353 & #560 do not execute nested query if key is null
      if (propValue != null) {
        metaObject.setValue(innerResultMapping.getProperty(), propValue);
        foundValues = true;
      }
    }
    return foundValues ? parameterObject : null;
  }

  /**
   * 实例化parameterType指定的形参对象
   *
   * @param parameterType
   * @return
   */
  private Object instantiateParameterObject(Class<?> parameterType) {
    // 没有指定parameterType属性值，则默认为HashMap类型
    if (parameterType == null) {
      return new HashMap<>();
    } else if (ParamMap.class.equals(parameterType)) {
      return new HashMap<>(); // issue #649
    } else {
      // 创建并返回形参对象
      return objectFactory.create(parameterType);
    }
  }

  //
  // DISCRIMINATOR
  //

  /**
   * 根据该行记录以及 {@link ResultMap#discriminator}，选择映射使用后的结果对象，这个选择可能存在多层嵌套
   *
   * @param rs
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix) throws SQLException {
    // 记录已经处理过ResultMap的id
    Set<String> pastDiscriminators = new HashSet<>();
    // 获取该ResultMap下的Discriminator对象
    Discriminator discriminator = resultMap.getDiscriminator();
    while (discriminator != null) {
      // 映射<discrimination>指定列的值
      final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);
      // 如果<case>节点配置了resultMap属性值，根据该值获取相应ResultMap的id，否则获取的结果为该到对应case指定值的完整id
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      // 判断是否有鉴别器指定的ResultMap对象
      if (configuration.hasResultMap(discriminatedMapId)) {
        // 获取鉴别器指定的ResultMap对象并覆盖原 resultMap变量
        resultMap = configuration.getResultMap(discriminatedMapId);
        // 当前Discriminator对象
        Discriminator lastDiscriminator = discriminator;
        // 当前鉴别器指定的ResultMap对象的鉴别器（可以理解为下一个鉴别器）
        discriminator = resultMap.getDiscriminator();
        // pastDiscriminators.add失败之后即表示pastDiscriminators原已经存在该discriminatedMapId
        // 检测Discriminator是否出现了环形引用，即当前鉴别器指定的ResultMap对象的鉴别器就是当前鉴别器
        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
          break;
        }
      } else {  // discriminator获取的值没有对应ResultMap对象
        break;
      }
    }
    return resultMap;
  }

  /**
   * 映射鉴别器的值
   * <p>
   * 使用 {@link TypeHandler}获取Discriminator配置的列值并将该值转换成对应的java类型
   *
   * @param rs
   * @param discriminator
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix) throws SQLException {
    final ResultMapping resultMapping = discriminator.getResultMapping();
    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  /**
   * 列名前添加前缀
   *
   * @param columnName 列名
   * @param prefix     前缀
   * @return
   */
  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }

  //
  // HANDLE NESTED RESULT MAPS
  //

  /**
   * 含嵌套映射的多结果处理 <br>
   * 步骤如下：<br>
   * (1)通过 {@link DefaultResultSetHandler#skipRows}方法定位到指定的记录行。<br>
   * (2)通过{@link DefaultResultSetHandler#shouldProcessMoreRows}方法检测是否能继续映射结果集中剩余的记录行。<br>
   * (3)调用{@link DefaultResultSetHandler#resolveDiscriminatedResultMap}方法，它根据 {@link ResultMap}中记录的 {@link Discriminator}
   * 对象以及参与映射的记录行中相应的列值，决定映射使用的 {@link ResultMap}对象。<br>
   * (4)通过{@link DefaultResultSetHandler#createRowKey}方法为该行记录生成 {@link CacheKey}, {@link CacheKey}除了作为缓存中的key
   * 值,在嵌套映射中也作为key唯一标识一个结果对象。<br>
   * (5)根据步骤(4)生成的 {@link CacheKey}查询{@link DefaultResultSetHandler#nestedResultObjects}集合。在处理嵌套映射过程
   * 中生成的所有结果对象(包括嵌套映射生成的对象)，都会生成相应的 {@link CacheKey}并保存到该集合中。
   * (6)检测 &lt;select> 节点中 resultOrdered 属性的配置，该设置仅针对嵌套映射有效。当resultOrdered属性为true 时，
   * 则认为返回一个主结果行时，不会发生引用{@link DefaultResultSetHandler#nestedResultObjects}集合中对象的情况。这样就提前释
   * 放了{@link DefaultResultSetHandler#nestedResultObjects}集合中的数据，避免在进行嵌套映射出现内存不足的情况。<br>
   * (7)通过调用{@link DefaultResultSetHandler#getRowValue}方法的另一重载方法，完成当前记录行的映射操作并返回结果对象，
   * 其中还会将结果对象添加到{@link DefaultResultSetHandler#nestedResultObjects}集合中。<br>
   * (8)通过{@link DefaultResultSetHandler#storeObject}方法将生成的结果对象保存到 {@link ResultHandler}中。<br>
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   * @throws SQLException
   */
  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    // 默认结果上下文对象
    final DefaultResultContext<Object> resultContext = new DefaultResultContext<>();
    ResultSet resultSet = rsw.getResultSet();
    // (1)：移动游标定位到指定的记录行
    skipRows(resultSet, rowBounds);
    // 记录上一条记录映射的主结果对象
    Object rowValue = previousRowValue;
    // (2)：检测是否能继续映射结果集中剩余的记录行
    while (shouldProcessMoreRows(resultContext, rowBounds) && !resultSet.isClosed() && resultSet.next()) {
      // (3)：通过鉴别器决定使用哪个ResultMap对象
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(resultSet, resultMap, null);
      // (4)：为该行记录生成CacheKey对象
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
      // (5)：根据步骤4中生成的CacheKey查找nestedResultObjects集合
      Object partialObject = nestedResultObjects.get(rowKey);
      // issue #577 && #542
      // 当resultOrdered属性为true且主结果发生变化时，则清空nestedResultObjects集合
      if (mappedStatement.isResultOrdered()) {  // (6)：检测resultOrdered属性，该属性只对嵌套映射有效，resultOrdered的作用参考书籍275页的说明
        if (partialObject == null && rowValue != null) {  // 主结果发生变化
          nestedResultObjects.clear();  // 清空nestedResultObjects集合
          // 保存主结果对象，即嵌套映射的外层循环
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
        // (7)：完成该行记录的映射返回结果对象
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
      } else {
        // (7)
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
        if (partialObject == null) {
          // (8)：保存结果对象
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
      }
    }
    // 对resultOrdered属性为true时的特殊处理，调用storeObject()方法保存结果对象
    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
      previousRowValue = null;
    } else if (rowValue != null) {
      // 记录本次映射的外层对象，用于下一次判断查询外层对象是否发生改变，然后根据resultOrdered的值决定是否清空nestedResultObjects集合
      previousRowValue = rowValue;
    }
  }

  //
  // GET VALUE FROM ROW FOR NESTED RESULT MAP
  //

  /**
   * 对结果集中的一行记录进行嵌套映射
   * <p>
   * 如果外层对象存在，则直接进行嵌套映射，不存在则先创建外层对象并进行简单映射（此步骤和另外一个重载方法类似）再进行嵌套映射
   *
   * @param rsw
   * @param resultMap
   * @param combinedKey
   * @param columnPrefix
   * @param partialObject
   * @return
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, CacheKey combinedKey, String columnPrefix, Object partialObject) throws SQLException {
    final String resultMapId = resultMap.getId();
    Object rowValue = partialObject;
    if (rowValue != null) { // 如果外层对象（已映射部分属性）存在，则直接嵌套映射
      final MetaObject metaObject = configuration.newMetaObject(rowValue);
      // 嵌套映射之前，将外层结果添加到 ancestorObjects 集合中
      putAncestor(rowValue, resultMapId);
      // 处理嵌套映射，将生成的结果设置到外层对象相应的属性只
      applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, false);
      // 移除外层对象
      ancestorObjects.remove(resultMapId);
    } else {  // 外层对象不存在，先进行简单映射，再嵌套映射
      final ResultLoaderMap lazyLoader = new ResultLoaderMap();
      // 不存在则创建外层对象
      rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
      // 外层对象没有对应的类型处理器才处理
      if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
        final MetaObject metaObject = configuration.newMetaObject(rowValue);
        boolean foundValues = this.useConstructorMappings;
        // 检测是否开启了自动映射，第二个参数为true（嵌套）
        if (shouldApplyAutomaticMappings(resultMap, true)) {
          // 自动映射未明确的列名
          foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
        }
        // 映射明确的列名，到此对象的部分映射（非嵌套映射）以完成
        foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
        // 嵌套映射之前，将外层结果添加到 ancestorObjects 集合中
        putAncestor(rowValue, resultMapId);
        // 处理嵌套映射，将生成的结果设置到外层对象相应的属性
        foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
        // 移除外层对象
        ancestorObjects.remove(resultMapId);
        foundValues = lazyLoader.size() > 0 || foundValues;
        rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
      }
      if (combinedKey != CacheKey.NULL_CACHE_KEY) {
        // 将外层对象保存到 nestedResultObjects集合中，到后续同一外层对应的相应记录使用
        nestedResultObjects.put(combinedKey, rowValue);
      }
    }
    return rowValue;
  }

  private void putAncestor(Object resultObject, String resultMapId) {
    ancestorObjects.put(resultMapId, resultObject);
  }

  //
  // NESTED RESULT MAP (JOIN MAPPING)
  //

  private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
    boolean foundValues = false;
    // 遍历ResultMap的getPropertyResultMappings集合处理其中的嵌套映射的项
    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
      // 获取嵌套resultMap的id，不为空则表示该ResultMapping存在嵌套映射
      final String nestedResultMapId = resultMapping.getNestedResultMapId();
      // 步骤1：校验是否存在嵌套映射，多结果集处理不属于嵌套映射
      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
        try {
          // 获取列前缀
          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
          // 步骤2：根据鉴别器确定使用的 ResultMap对象
          final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);
          // 处理循环引用的情况
          if (resultMapping.getColumnPrefix() == null) {
            // try to fill circular reference only when columnPrefix
            // is not specified for the nested result map (issue #215)
            // 如果 ancestorObject 不为null，则表示当前映射的嵌套对象在之前已经进行过映射，可重用之前映射产生的对象。
            // 可以防止相互引用之间映射的问题，比如A中有B属性，B中有A属性，在A中先嵌套映射B，嵌套映射B的A属性值，发现A已经存在，则直接把A对象赋值到B对象的A属性
            Object ancestorObject = ancestorObjects.get(nestedResultMapId);
            if (ancestorObject != null) {
              if (newObject) {
                linkObjects(metaObject, resultMapping, ancestorObject); // issue #385
              }
              // 若是循环引用，则不用执行下面的代码创建对象，而是重用之前的对象
              continue;
            }
          }
          // 步骤4：为嵌套对象创建使用的CacheKey
          final CacheKey rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);
          // 步骤4.1：与外层对象的CacheKey合并，得到全局唯一的CacheKey对象
          final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);
          // 查找 nestedResultObjects 集合中是否拥有相同的key（相同的嵌套对象）
          Object rowValue = nestedResultObjects.get(combinedKey);
          boolean knownValue = rowValue != null;
          // 步骤5：如果外层对象的嵌套对象属性为 Collection类型，且未初始化化，则初始化空集合，以便在调用linkObjects方法时候直接把结果加入集合
          instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject); // mandatory
          // 步骤6：根据<association>和<collectin>节点的notNullColumn属性值检测结果集中的空值
          if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw)) {
            // 步骤7：完成嵌套映射，并生成嵌套对象，嵌套映射可以嵌套多层，可能产生多层递归
            rowValue = getRowValue(rsw, nestedResultMap, combinedKey, columnPrefix, rowValue);
            /*
                attention，“!knownValue"这个条件，当嵌套对象已存在于 nestedResultObjects 集合中时，说明相关列已经映射成了嵌套对象。
                现假设对象A中有b1和b2两个属性都指向了对象B，且这两个属性都是由同一ResultMap进行映射的。在对一行记录进行映射时，
                首先映射的bl属性会生成B对象且成功赋值，而b2属性则为null。
            */
            if (rowValue != null && !knownValue) {
              // 步骤8：将步骤7中得到的嵌套对象保存到外层对象的相应属性中
              linkObjects(metaObject, resultMapping, rowValue);
              foundValues = true;
            }
          }
        } catch (SQLException e) {
          throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
        }
      }
    }
    return foundValues;
  }

  /**
   * 父层 {@link ResultMapping} 对象列前缀拼接当前层{@link ResultMapping}列前缀
   *
   * @param parentPrefix
   * @param resultMapping
   * @return
   */
  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    if (parentPrefix != null) {
      columnPrefixBuilder.append(parentPrefix);
    }
    if (resultMapping.getColumnPrefix() != null) {
      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    }
    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
  }

  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSetWrapper rsw) throws SQLException {
    Set<String> notNullColumns = resultMapping.getNotNullColumns();
    if (notNullColumns != null && !notNullColumns.isEmpty()) {
      ResultSet rs = rsw.getResultSet();
      for (String column : notNullColumns) {
        rs.getObject(prependPrefix(column, columnPrefix));
        if (!rs.wasNull()) {
          return true;
        }
      }
      return false;
    } else if (columnPrefix != null) {
      for (String columnName : rsw.getColumnNames()) {
        if (columnName.toUpperCase().startsWith(columnPrefix.toUpperCase())) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  /**
   * 获取嵌套 {@link ResultMap}的鉴别器指定的 {@link ResultMap}
   *
   * @param rs
   * @param nestedResultMapId
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix) throws SQLException {
    ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
    return resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
  }

  //
  // UNIQUE RESULT KEY
  //

  /**
   * (1)尝试使用<idArg>节点或<id>节点中定义的列名以及对应结果集中的列值组成 {@link CacheKey}对象。<br>
   * (2)如果 {@link ResultMap}中没有定义<idArg>节点或<id>节点，则由 {@link ResultMap} 中明确要映射的列名以及
   * 对应结果集中列值一起构成 {@link CacheKey}对象。<br>
   * (3)(1)和(2)，依然查找不到相关的列名和列值，且 {@link ResultMap#type} 属性明确指明了结果对象为 {@link Map}类型，
   * 则由结果集中所有列名以及该行记录行的所有列值一起构成 {@link CacheKey}对象。<br>
   * (4)如果映射的结果对象不是 {@link Map}类型，则由结果集中未明确映射的列名以及它们在当前记录行中的对应列值-起构成 {@link CacheKey}对象。<br>
   *
   * @param resultMap
   * @param rsw
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
    final CacheKey cacheKey = new CacheKey();
    // 更新CacheKey的updateList集合
    cacheKey.update(resultMap.getId());
    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
    if (resultMappings.isEmpty()) {
      if (Map.class.isAssignableFrom(resultMap.getType())) {
        // 处理返回结果是Map的情况
        createRowKeyForMap(rsw, cacheKey);
      } else {
        // 处理未明确列名的情况
        createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
      }
    } else {
      // 处理列名明确的情况
      createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
    }
    if (cacheKey.getUpdateCount() < 2) {
      return CacheKey.NULL_CACHE_KEY;
    }
    return cacheKey;
  }

  /**
   * 合并 {@link CacheKey}
   *
   * @param rowKey
   * @param parentRowKey
   * @return
   */
  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
    // 边界检查
    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
      CacheKey combinedKey;
      try {
        // 获取 rowKey 的克隆对象
        combinedKey = rowKey.clone();
      } catch (CloneNotSupportedException e) {
        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
      }
      // 添加外层映射相关的CacheKey对象
      combinedKey.update(parentRowKey);
      return combinedKey;
    }
    return CacheKey.NULL_CACHE_KEY;
  }

  /**
   * 首先检查 {@link ResultMap}中是否定义了<idArg>节点或<id>节点，如果是则返回ResultMap.getIdResultMappings 集合，
   * 否则返回ResultMap.getPropertyResultMappings 集合。
   *
   * @param resultMap
   * @return
   */
  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
    if (resultMappings.isEmpty()) {
      resultMappings = resultMap.getPropertyResultMappings();
    }
    return resultMappings;
  }

  /**
   * 将明确映射的列名和列值加入到 {@link CacheKey.update}集合中保存，其中可能存在嵌套处理
   *
   * @param resultMap
   * @param rsw
   * @param cacheKey
   * @param resultMappings
   * @param columnPrefix
   * @throws SQLException
   */
  private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
    for (ResultMapping resultMapping : resultMappings) {
      if (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null) { // 嵌套映射
        // Issue #392
        final ResultMap nestedResultMap = configuration.getResultMap(resultMapping.getNestedResultMapId());
        // 嵌套处理
        createRowKeyForMappedProperties(nestedResultMap, rsw, cacheKey, nestedResultMap.getConstructorResultMappings(),
          prependPrefix(resultMapping.getColumnPrefix(), columnPrefix));
      } else if (resultMapping.getNestedQueryId() == null) {  // 忽略嵌套查询的情况
        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
        final TypeHandler<?> th = resultMapping.getTypeHandler();
        // 获取已经映射的列名
        List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
        // Issue #114
        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
          final Object value = th.getResult(rsw.getResultSet(), column);
          if (value != null || configuration.isReturnInstanceForEmptyRow()) {
            // 将映射后的列名和列值更新到updateList集合
            cacheKey.update(column);
            cacheKey.update(value);
          }
        }
      }
    }
  }

  /**
   * 将未明确映射的列名和列值加入到 {@link CacheKey.update}集合中保存
   *
   * @param resultMap
   * @param rsw
   * @param cacheKey
   * @param columnPrefix
   * @throws SQLException
   */
  private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, String columnPrefix) throws SQLException {
    // 获取返回结果类型的元信息
    final MetaClass metaType = MetaClass.forClass(resultMap.getType(), reflectorFactory);
    // 获取未映射的列名
    List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    for (String column : unmappedColumnNames) {
      String property = column;
      // 如果配置了列前缀，则此时column和property不相同，则需要截掉列前缀
      if (columnPrefix != null && !columnPrefix.isEmpty()) {
        // When columnPrefix is specified, ignore columns without the prefix.
        if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          property = column.substring(columnPrefix.length());
        } else {  // 配置了列前缀但实际查询的列名或别名却不是以列前缀开头，即列名映射和java属性名称映射不匹配，注定映射不成功的
          continue;
        }
      }
      // 判断去掉前缀的部分是否就是java属性的名称，即去掉前缀的列名是否与java属性是否一一映射
      if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
        String value = rsw.getResultSet().getString(column);
        if (value != null) {  // 将映射后的列名和列值更新到updateList集合
          cacheKey.update(column);
          cacheKey.update(value);
        }
      }
    }
  }

  /**
   * 把结果集的所有列名和列值加入到 {@link CacheKey.update}集合中保存
   *
   * @param rsw
   * @param cacheKey
   * @throws SQLException
   */
  private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
    List<String> columnNames = rsw.getColumnNames();
    for (String columnName : columnNames) {
      final String value = rsw.getResultSet().getString(columnName);
      if (value != null) {
        cacheKey.update(columnName);
        cacheKey.update(value);
      }
    }
  }

  /**
   * 将已存在的嵌套对象设置到外层对象的相应属性中。
   *
   * @param metaObject
   * @param resultMapping
   * @param rowValue
   */
  private void linkObjects(MetaObject metaObject, ResultMapping resultMapping, Object rowValue) {
    // 检查外层对象的指定属性是否为Collection类型，如果是且未初始化，则初始化该集合属性并返回
    final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);
    // 根据属性是否为集合类型，调用MetaObject的相应方法，将嵌套对象记录到外层对象的相应属性中
    if (collectionProperty != null) {
      // 将嵌套对象加入到外层对象的集合中
      final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
      targetMetaObject.add(rowValue);
    } else {
      // 将嵌套映射的对象赋值到外层对象
      metaObject.setValue(resultMapping.getProperty(), rowValue);
    }
  }

  /**
   * 实例化集合属性并赋值到相应属性中
   *
   * @param resultMapping
   * @param metaObject
   * @return
   */
  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
    // 获取相应java属性名
    final String propertyName = resultMapping.getProperty();
    // 根据属性名获取相应属性值
    Object propertyValue = metaObject.getValue(propertyName);
    if (propertyValue == null) {  // 为null，表示相应属性尚未初始化
      Class<?> type = resultMapping.getJavaType();
      if (type == null) { // 优先使用javaType属性值配置的集合类型，否则使用该属性值对应setter方法的形参类型
        type = metaObject.getSetterType(propertyName);
      }
      try {
        // 判断如果是Collection类型，则实例化相应集合并赋值到相应属性中
        if (objectFactory.isCollection(type)) {
          // 创建集合
          propertyValue = objectFactory.create(type);
          // 将新建的空集合设置外层对象对应属性中
          metaObject.setValue(propertyName, propertyValue);
          return propertyValue;
        }
      } catch (Exception e) {
        throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
      }
    } else if (objectFactory.isCollection(propertyValue.getClass())) {  // 相应属性值不为null，且值为Collection类型，直接返回
      return propertyValue;
    }
    // 属性非Collection类型，返回null
    return null;
  }

  /**
   * 判断结果对象是否有对应的 {@link TypeHandler}对象
   *
   * @param rsw
   * @param resultType
   * @return
   */
  private boolean hasTypeHandlerForResultObject(ResultSetWrapper rsw, Class<?> resultType) {
    if (rsw.getColumnNames().size() == 1) {
      return typeHandlerRegistry.hasTypeHandler(resultType, rsw.getJdbcType(rsw.getColumnNames().get(0)));
    }
    return typeHandlerRegistry.hasTypeHandler(resultType);
  }

}
