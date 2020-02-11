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
package org.apache.ibatis.builder;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

import java.util.*;

/**
 * Mapper构建助手，常被用于 {@link XMLMapperBuilder}和 {@link MapperAnnotationBuilder}类，主要负责创建 {@link Cache}、{@link ResultMap}
 * {@link ResultMapping}、 {@link ParameterMap}、 {@link ParameterMapping}和 {@link MappedStatement}等
 *
 * @author Clinton Begin
 */
public class MapperBuilderAssistant extends BaseBuilder {

  /**
   * 映射文件的命名空间
   */
  private String currentNamespace;
  /**
   * 映射文件的资源路径，resource或url
   */
  private final String resource;
  /**
   * 当前映射文件的缓存对象
   */
  private Cache currentCache;
  /**
   * 是否未成功解析{@link Cache}引用，false表示成功解析{@link Cache}引用
   */
  private boolean unresolvedCacheRef; // issue #676

  public MapperBuilderAssistant(Configuration configuration, String resource) {
    super(configuration);
    ErrorContext.instance().resource(resource);
    this.resource = resource;
  }

  public String getCurrentNamespace() {
    return currentNamespace;
  }

  public void setCurrentNamespace(String currentNamespace) {
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '"
        + this.currentNamespace + "' but found '" + currentNamespace + "'.");
    }

    this.currentNamespace = currentNamespace;
  }

  /**
   * 拼接完整的属性值，格式：namespace+"."+base
   *
   * @param base        <resultMap>属性值
   * @param isReference
   * @return
   */
  public String applyCurrentNamespace(String base, boolean isReference) {
    if (base == null) {
      return null;
    }
    if (isReference) {
      // is it qualified with any namespace yet?
      // 校验<resultMap>节点是否是完整的resultMap的属性名
      if (base.contains(".")) {
        return base;
      }
    } else {
      // is it qualified with this namespace yet?
      // 校验<resultMap>节点是否是完整的resultMap的属性名
      if (base.startsWith(currentNamespace + ".")) {
        return base;
      }
      // 如果<resultMap>节点id属性值报刊了“.”且又不是完整的id属性值，则抛出异常
      if (base.contains(".")) {
        throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
      }
    }
    // 拼接<resultMap>节点的完整id属性值
    return currentNamespace + "." + base;
  }

  /**
   * 根据namespace（Cache的id）从{@link Configuration} 对象中缓存获取对应的缓存对象
   *
   * @param namespace 被引用缓存所在的Mapper映射文件所在的命名空间
   * @return
   */
  public Cache useCacheRef(String namespace) {
    // namespace为null则抛出异常
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      // 标识未成功解析Cache引用
      unresolvedCacheRef = true;
      // 通过namespace（Configuration中caches集合的key）获取被引用的缓存对象
      Cache cache = configuration.getCache(namespace);
      if (cache == null) {
        throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
      }
      currentCache = cache;
      // 标识已成功解析Cache引用
      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }

  /**
   * @param typeClass
   * @param evictionClass
   * @param flushInterval
   * @param size
   * @param readWrite
   * @param blocking
   * @param props
   * @return
   */
  public Cache useNewCache(Class<? extends Cache> typeClass, Class<? extends Cache> evictionClass, Long flushInterval, Integer size,
                           boolean readWrite, boolean blocking, Properties props) {
    // 创建Cache对象，这里使用了建造者模式，CacheBuilder是建造者的角色，而Cache是生成的产品
    // 映射文件的命名空间作为缓存的唯一标识
    Cache cache = new CacheBuilder(currentNamespace)
      // 通过链式调用初始化CacheBuilder相应的字段
      .implementation(valueOrDefault(typeClass, PerpetualCache.class))
      .addDecorator(valueOrDefault(evictionClass, LruCache.class))
      .clearInterval(flushInterval)
      .size(size)
      .readWrite(readWrite)
      .blocking(blocking)
      .properties(props)
      .build();
    // 将Cache对象添加到Configuration.caches集合中保存,其中会将Cache的id作为key,Cache对象本身作为value
    configuration.addCache(cache);
    // 记录当前命名空间使用的cache对象
    currentCache = cache;
    return cache;
  }

  @Deprecated
  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    id = applyCurrentNamespace(id, false);
    ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }

  public ParameterMapping buildParameterMapping(
    Class<?> parameterType,
    String property,
    Class<?> javaType,
    JdbcType jdbcType,
    String resultMap,
    ParameterMode parameterMode,
    Class<? extends TypeHandler<?>> typeHandler,
    Integer numericScale) {
    resultMap = applyCurrentNamespace(resultMap, true);

    // Class parameterType = parameterMapBuilder.type();
    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    return new ParameterMapping.Builder(configuration, property, javaTypeClass)
      .jdbcType(jdbcType)
      .resultMapId(resultMap)
      .mode(parameterMode)
      .numericScale(numericScale)
      .typeHandler(typeHandlerInstance)
      .build();
  }

  public ResultMap addResultMap(
    String id,
    Class<?> type,
    String extend,
    Discriminator discriminator,
    List<ResultMapping> resultMappings,
    Boolean autoMapping) {
    // resultMap完整的id属性值是：“namespace.id”的格式
    id = applyCurrentNamespace(id, false);
    // resultMap完整的extend属性值是：“namespace.extend”的格式
    extend = applyCurrentNamespace(extend, true);

    if (extend != null) {
      // 如果继承的resultMap不存在，则抛出异常
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      // 获取继承的ResultMap的ResultMapping集合，通过以下代码可知继承的<resultMap>节点的子节点<discriminator>不会被继承
      ResultMap resultMap = configuration.getResultMap(extend);
      List<ResultMapping> extendedResultMappings = new ArrayList<>(resultMap.getResultMappings());
      // 移除该ResultMap覆盖的ResultMapping集合
      extendedResultMappings.removeAll(resultMappings);
      // Remove parent constructor if this resultMap declares a constructor.
      // 如果此resultMap声明了构造函数，则删除父构造函数相应的ResultMapping对象
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      if (declaresConstructor) {
        // 删除父构造函数相应的ResultMapping对象，即带ResultFlag.CONSTRUCTOR标志的ResultMapping对象
        extendedResultMappings.removeIf(resultMapping -> resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR));
      }
      // 合并父子类的ResultMapping对象
      resultMappings.addAll(extendedResultMappings);
    }
    ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
      .discriminator(discriminator)
      .build();
    configuration.addResultMap(resultMap);
    return resultMap;
  }

  /**
   * 保存<discriminator>的子节点<case>value属性和resultMap属性值的映射关系
   *
   * @param resultType
   * @param column
   * @param javaType
   * @param jdbcType
   * @param typeHandler
   * @param discriminatorMap
   * @return
   */
  public Discriminator buildDiscriminator(
    Class<?> resultType,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    Class<? extends TypeHandler<?>> typeHandler,
    Map<String, String> discriminatorMap) {
    // 构建<discriminator>节点对应的ResultMapping对象
    ResultMapping resultMapping = buildResultMapping(
      resultType,
      null,
      column,
      javaType,
      jdbcType,
      null,
      null,
      null,
      null,
      typeHandler,
      new ArrayList<>(),
      null,
      null,
      false);
    Map<String, String> namespaceDiscriminatorMap = new HashMap<>();
    // 遍历拼接<discriminator>的子节点<case>指定的resultMap属性值的完整resultMap的id
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      String resultMap = e.getValue();
      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
  }

  public MappedStatement addMappedStatement(
    String id,
    SqlSource sqlSource,
    StatementType statementType,
    SqlCommandType sqlCommandType,
    Integer fetchSize,
    Integer timeout,
    String parameterMap,
    Class<?> parameterType,
    String resultMap,
    Class<?> resultType,
    ResultSetType resultSetType,
    boolean flushCache,
    boolean useCache,
    boolean resultOrdered,
    KeyGenerator keyGenerator,
    String keyProperty,
    String keyColumn,
    String databaseId,
    LanguageDriver lang,
    String resultSets) {

    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }

    id = applyCurrentNamespace(id, false);
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
      .resource(resource)
      .fetchSize(fetchSize)
      .timeout(timeout)
      .statementType(statementType)
      .keyGenerator(keyGenerator)
      .keyProperty(keyProperty)
      .keyColumn(keyColumn)
      .databaseId(databaseId)
      .lang(lang)
      .resultOrdered(resultOrdered)
      .resultSets(resultSets)
      .resultMaps(getStatementResultMaps(resultMap, resultType, id))
      .resultSetType(resultSetType)
      .flushCacheRequired(valueOrDefault(flushCache, !isSelect))
      .useCache(valueOrDefault(useCache, isSelect))
      .cache(currentCache);

    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }

    MappedStatement statement = statementBuilder.build();
    configuration.addMappedStatement(statement);
    return statement;
  }

  private <T> T valueOrDefault(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  private ParameterMap getStatementParameterMap(
    String parameterMapName,
    Class<?> parameterTypeClass,
    String statementId) {
    parameterMapName = applyCurrentNamespace(parameterMapName, true);
    ParameterMap parameterMap = null;
    if (parameterMapName != null) {
      try {
        parameterMap = configuration.getParameterMap(parameterMapName);
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find parameter map " + parameterMapName, e);
      }
    } else if (parameterTypeClass != null) {
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      parameterMap = new ParameterMap.Builder(
        configuration,
        statementId + "-Inline",
        parameterTypeClass,
        parameterMappings).build();
    }
    return parameterMap;
  }

  private List<ResultMap> getStatementResultMaps(
    String resultMap,
    Class<?> resultType,
    String statementId) {
    resultMap = applyCurrentNamespace(resultMap, true);

    List<ResultMap> resultMaps = new ArrayList<>();
    if (resultMap != null) {
      String[] resultMapNames = resultMap.split(",");
      for (String resultMapName : resultMapNames) {
        try {
          resultMaps.add(configuration.getResultMap(resultMapName.trim()));
        } catch (IllegalArgumentException e) {
          throw new IncompleteElementException("Could not find result map '" + resultMapName + "' referenced from '" + statementId + "'", e);
        }
      }
    } else if (resultType != null) {
      ResultMap inlineResultMap = new ResultMap.Builder(
        configuration,
        statementId + "-Inline",
        resultType,
        new ArrayList<>(),
        null).build();
      resultMaps.add(inlineResultMap);
    }
    return resultMaps;
  }

  public ResultMapping buildResultMapping(
    Class<?> resultType,  // <resultMap>节点type属性指定的Class对象
    String property,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    String nestedSelect,
    String nestedResultMap,
    String notNullColumn,
    String columnPrefix,
    Class<? extends TypeHandler<?>> typeHandler,
    List<ResultFlag> flags,
    String resultSet,
    String foreignColumn,
    boolean lazy) {

    // 解析property属性的setter方法的形参Class对象（获取resultType类字段的setter方法形参类型）
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    // 根据javaTypeClass（字段setter方法的形参类型）获取对应的 TypeHandler对象
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    /*
      解析 column 属性值，当column是" {prop1=col1, prop2=col2}"形式时，会解析成 ResultMapping对象集合，
      column的这种形式主要用于嵌套查询的参数传递。
     */
    List<ResultMapping> composites;
    if ((nestedSelect == null || nestedSelect.isEmpty()) && (foreignColumn == null || foreignColumn.isEmpty())) {
      composites = Collections.emptyList();
    } else {
      composites = parseCompositeColumnName(column);
    }
    // 创建 ResultMapping 对象，并设置其字段
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
      .jdbcType(jdbcType)
      .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
      .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
      .resultSet(resultSet)
      .typeHandler(typeHandlerInstance)
      .flags(flags == null ? new ArrayList<>() : flags)
      .composites(composites)
      .notNullColumns(parseMultipleColumnNames(notNullColumn))
      .columnPrefix(columnPrefix)
      .foreignColumn(foreignColumn)
      .lazy(lazy)
      .build();
  }

  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }

  /**
   * 解析复合列名，默认使用 {@link UnknownTypeHandler}未知类型处理器，此类解析算法参考 {@link com.huazai.test.basis.MyTest}
   *
   * @param columnName column属性值指定的符合列名
   * @return
   */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    List<ResultMapping> composites = new ArrayList<>();
    // 是否匹配column是"{prop1=col1, prop2=co12}"形式
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      while (parser.hasMoreTokens()) {
        // 获取“=”左边的键，即类字段名
        String property = parser.nextToken();
        // 获取“=”右边的值，即数据库列名或别名
        String column = parser.nextToken();
        ResultMapping complexResultMapping = new ResultMapping.Builder(
          configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
        composites.add(complexResultMapping);
      }
    }
    return composites;
  }

  /**
   * 如果指定了javaType属性值，则返回该属性值类型对应的Class对象，否则返回property属性值指定的字段的setter方法形参的Class对象
   * <p>
   * 如果property和javaType都没有指定，或指定的不存在则返回Object.Class对象
   *
   * @param resultType
   * @param property
   * @param javaType
   * @return
   */
  private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
    if (javaType == null && property != null) {
      try {
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        javaType = metaResultType.getSetterType(property);
      } catch (Exception e) {
        // 不处理异常，即：<id>和<result>等节点的property属性值指定了不存在的javabean字段，也不会报错
        //ignore, following null check statement will deal with the situation
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
    if (javaType == null) {
      if (JdbcType.CURSOR.equals(jdbcType)) {
        javaType = java.sql.ResultSet.class;
      } else if (Map.class.isAssignableFrom(resultType)) {
        javaType = Object.class;
      } else {
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        javaType = metaResultType.getGetterType(property);
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  /**
   * Backward compatibility signature.
   */
  public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
                                          JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
                                          Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags) {
    return buildResultMapping(
      resultType, property, column, javaType, jdbcType, nestedSelect,
      nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
  }

  /**
   * @deprecated Use {@link Configuration#getLanguageDriver(Class)}
   */
  @Deprecated
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    return configuration.getLanguageDriver(langClass);
  }

  /**
   * Backward compatibility signature.
   */
  public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                            SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                            String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                            boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                            LanguageDriver lang) {
    return addMappedStatement(
      id, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
      parameterMap, parameterType, resultMap, resultType, resultSetType,
      flushCache, useCache, resultOrdered, keyGenerator, keyProperty,
      keyColumn, databaseId, lang, null);
  }

}
