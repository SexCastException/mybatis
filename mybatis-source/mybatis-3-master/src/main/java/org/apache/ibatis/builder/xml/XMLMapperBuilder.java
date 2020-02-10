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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.*;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.io.InputStream;
import java.io.Reader;
import java.util.*;

/**
 * XML配置方式的Mapper构造器，注解方式 See {@link MapperAnnotationBuilder}
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  /**
   *
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * 记录了<sql>节点的id和封装该节点的{@link XNode}对象的映射关系
   */
  private final Map<String, XNode> sqlFragments;
  /**
   * 映射文件的资源路径，resource或url
   */
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  /**
   * 多个public重载构造函数，最终会调用此私有构造函数
   *
   * @param parser
   * @param configuration
   * @param resource
   * @param sqlFragments
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 解析映射文件的入口
   */
  public void parse() {
    // 判断是否已经加载过该映射文件
    if (!configuration.isResourceLoaded(resource)) {
      // 解析映射文件的根节点 <mapper>
      configurationElement(parser.evalNode("/mapper"));
      // 将已经解析过的Mapper映射文件资源路径 resource 保存到全局映射文件的loadedResources集合中
      configuration.addLoadedResource(resource);
      // 注册resource指定的Mapper映射文件对应的Mapper接口
      bindMapperForNamespace();
    }

    // 处理 configurationElement() 方法中解析失败的<resultMap>节点
    parsePendingResultMaps();
    // 处理 configurationElement() 方法中解析失败的<cache-ref>节点
    parsePendingCacheRefs();
    // 处理 configurationElement() 方法中解析失败的SQL语句节点
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  private void configurationElement(XNode context) {
    try {
      // 获取<mappers>标签的 namespace 属性值，必须指定，否则抛出异常
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // 设置 MapperBuilderAssistant 的 currentNamespace 字段，记录当前命名空间
      builderAssistant.setCurrentNamespace(namespace);
      // 解析<cache-ref>节点
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析<cache>节点
      cacheElement(context.evalNode("cache"));
      // 解析<mapper>子标签<parameterMap>，该节点已被废弃
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析<mapper>子标签<resultMap>
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析<mapper>子标签<sql>
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析<mapper>子标签<select>、<insert>、<update>和<delete>节点
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * 对于Statement节点（<select>、<update>、<delete>和<insert>）的解析，主要交由{@link XMLStatementBuilder}完成
   *
   * @param list
   * @param requiredDatabaseId
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // 遍历Statement节点
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * 映射文件的namespace与Cache对象之间的对应关系。如果我们希望多个namespace共用同一个二级缓存，即同一个Cache对象，
   * 则可以使用<cache-ref>节点进行配置。
   *
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 获取<cache-ref>标签的namespace属性值
      String namespace = context.getStringAttribute("namespace");
      // key是定义<cache-ref>所在映射文件的namespace，value是<cache-ref>节点的namespace属性所指定的namespace
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), namespace);
      // 创建 CacheRefResolver 对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, namespace);
      try {
        // 解析Cache引用，该过程主要是设置 MapperBuilderAssistant 中的currentCache和unresolvedCacheRef
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 如果解析过程出现异常，则添加到 Configuration.incompleteCacheRefs 集合，稍后再解析
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 使用配置文件方式创建缓存
   * <p>
   * 为映射文件每个命名空间创建一个对应的{@link Cache}对象
   * <p>
   * MyBatis默认情况下没有开启二级缓存，如果要为某命名空间开启二级缓存功能，则需要在相应映射配置文件中添加<cache>节点，
   * 还可以通过配置<cache>节点的相关属性，为二级缓存配置相应的特性(本质上就是添加相应的装饰器)。
   *
   * @param context
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      // 获取<cache>的 type 属性值，没有配置默认为：PERPETUAL（即PerpetualCache装饰器的别名）
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 通过type属性值获取缓存对象的类型（typeClass用于底层被装饰的缓存对象）
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // 获取<cache>的 eviction 属性值，没有配置默认为：LRU（即LruCache装饰器的别名）
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 通过eviction属性值获取缓存装饰器的类型（evictionClass 用于装饰缓存对象的装饰器）
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 获取<cache>的 flushInterval（清空缓存时间周期） 属性值，该值不为null时，则添加周期性清理缓存装饰器（ScheduledCache）
      Long flushInterval = context.getLongAttribute("flushInterval");
      // 获取<cache>的 size（缓存的个数） 属性值
      Integer size = context.getIntAttribute("size");
      // 获取<cache>的 readOnly（是否可读） 属性值，默认为true，为true时添加缓存序列化装饰器（SerializedCache）
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // 获取<cache>的 blocking（是否阻塞） 属性值，默认为false，为true时，添加缓存阻塞装饰器（BlockingCache）
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 获取封装<cache>的子标签<Properties>的name和value的属性值，用于初始化缓存或缓存装饰器对应的字段
      Properties props = context.getChildrenAsProperties();
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  @Deprecated
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * <resultMap> 节点下除了<discriminator>子节点的其他子节点，都会被解析成对应的{@link ResultMapping}对象。
   *
   * @param list
   * @throws Exception
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   * 解析映射文件中的全部<resultMap>节点，该方法会循环调用resultMapElement() 方法处理每个<resultMap>节点。
   *
   * @param resultMapNode
   * @param additionalResultMappings
   * @param enclosingType            封装类型
   * @return
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());

    // 获取<resultMap>节点的type属性，表示结果集将被映射成type指定类型的对象
    // type为null取ofType属性值，ofType为null取resultType属性值，resultType为null取javaType属性值
    // 主要做法是用于嵌套解析匿名ResultMap对象
    String type = resultMapNode.getStringAttribute("type",
      resultMapNode.getStringAttribute("ofType",
        resultMapNode.getStringAttribute("resultType",
          resultMapNode.getStringAttribute("javaType"))));
    // 通过type属性值获取对应的java类型对象，可以指定别名
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    // 用于记录解析后和嵌套解析之后的结果
    List<ResultMapping> resultMappings = new ArrayList<>();
    // 合并嵌套解析之后的结果
    resultMappings.addAll(additionalResultMappings);
    // 处理<resultMap>节点的子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        // 处理<constructor>节点
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        // 处理<discriminator>节点
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {  // 处理<association>、<collection>、<id>和<result>节点
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          // 如果是<id>节点，则向flags集合中添加 ResultFlag.ID
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    /*
      获取<resultMap>的id属性，如果id为null，则获取value属性值，获取不到则获取property属性值，并在次基础上拼接所有父节点名称
      如："mapper_resultMap[detailedBlogResultMap]_association[author]"
     */
    String id = resultMapNode.getStringAttribute("id",
      resultMapNode.getValueBasedIdentifier());
    // 获取<resultMap>节点的extends属性，该属性指定了该<resultMap>节点的继承关系。
    String extend = resultMapNode.getStringAttribute("extends");
    /*
      读取<resultMap>节点的 autoMapping 属性，将该属性设置为true，则启动自动映射功能，即自动查找与列名同名的属性名，并调用setter方法。
      而设置为false后，则需要在<resultMap>节点内明确注明映射关系才会调用对应的setter方法。
     */
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // 创建ResultMap对象，并添加到 configuration.resultMaps 集合中，该集合是 StrictMap类型
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 如果是&lt;association>节点，则获取该节点的property属性值，返回该属性值在 enclosingType类中对应的属性类型，
   * 如果是&lt;discriminator>节点，则直接返回enclosingType类型，
   * 否则返回null
   *
   * @param resultMapNode
   * @param enclosingType 外层封装类型
   * @return
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    // 如果是<resultMap>节点的子节点<association>，并且该节点的resultMap属性值没有配置
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
      // 如果是<discriminator>节点的子节点<association>，并且该节点的resultMap属性值没有配置
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      // 返回封装类型
      return enclosingType;
    }
    return null;
  }

  /**
   * @param resultChild    封装<constructor>节点的{@link XNode}对象
   * @param resultType     与数据库字段映射的javaBean对象
   * @param resultMappings 保存解析后的结果
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // 获取<constructor>节点的子节点（<idArg>和<arg>）
    List<XNode> argChildren = resultChild.getChildren();
    // 遍历子节点
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      // <constructor>的所有子节点添加ResultFlag.CONSTRUCTOR标志
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * @param context        封装<discriminator>节点的{@link XNode}对象
   * @param resultType     与数据库字段映射的javaBean对象
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // 获取<discriminator>节点的 column 属性值
    String column = context.getStringAttribute("column");
    // 获取<discriminator>节点的 javaType 属性值
    String javaType = context.getStringAttribute("javaType");
    // 获取<discriminator>节点的 jdbcType 属性值
    String jdbcType = context.getStringAttribute("jdbcType");
    // 获取<discriminator>节点的 typeHandler 属性值
    String typeHandler = context.getStringAttribute("typeHandler");

    // 获取jdbcType属性值对应的Class对象，可以使用别名
    Class<?> javaTypeClass = resolveClass(javaType);
    // 获取typeHandler属性值对应的Class对象，可以使用别名
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    // 获取jdbcType属性值对应的JdbcType对象
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);

    // 保存value属性和resultMap属性值的映射关系
    Map<String, String> discriminatorMap = new HashMap<>();
    // 遍历<discriminator>节点的 <case>节点
    for (XNode caseChild : context.getChildren()) {
      // 获取<case>节点的value属性值
      String value = caseChild.getStringAttribute("value");
      // 获取<case>节点的resultMap属性值
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   * 在映射配置文件中，可以使用<sql>节点定义可重用的SQL语句片段。点中定义的SQL语句片段时，只需要使用<include>节点引入相应的片段即可，这样，在编写
   *
   * @param list
   */
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    // 遍历所有的<sql>节点
    for (XNode context : list) {
      // 获取<sql>节点的 databaseId 属性值
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      // 为id添加命名空间
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 检测<sq1>的databaseId与当前Configuration中记录的databaseId是否一致
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * 检测<sq1>的databaseId与当前Configuration中记录的databaseId是否一致
   *
   * @param id
   * @param databaseId
   * @param requiredDatabaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    // 否则requiredDatabaseId 和 databaseId都为null，且此<sql>节点并没有加入sqlFragments集合中
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    // sqlFragments中已存在该<sql>节点，并且此时databaseId为null，如果此时databaseId对应的<sql>节点的databaseId为null，则与requiredDatabaseId相等
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * @param context
   * @param resultType <resultMap>节点type属性指定的Class对象
   * @param flags
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    //
    String property;
    // 如果是<constructor>节点则获取name属性值，否则获取property属性值，如<id>、<result>等
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    // 获取该节点对应的属性值（对应着<association>节点和<collection>的大多数属性）
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
      processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    // 根据fetchType属性判断获取 lazy属性，获取不到则根据全局配置文件判断是否为懒加载
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));

    // 解析 javaType 属性值，获取对应的Class对象
    Class<?> javaTypeClass = resolveClass(javaType);
    // 解析 typeHandler 属性值，获取对应的Class对象
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    // 解析 jdbcType 属性值，获取对应的JdbcType类型
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理嵌套的 ResultMap
   *
   * @param context        封装<association>、<collection>或<case>节点的{@link XNode}对象
   * @param resultMappings 保存解析的结果
   * @param enclosingType
   * @return
   * @throws Exception
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    // 只会处理<association>、 <collection>和<case>三种节点
    if ("association".equals(context.getName())
      || "collection".equals(context.getName())
      || "case".equals(context.getName())) {
      // 指定了select属性之后，不会生成嵌套的ResultMap对象
      if (context.getStringAttribute("select") == null) {
        // 校验<collection>节点的property属性是否在对应的javaBean中有该字段
        validateCollection(context, enclosingType);
        // 嵌套解析resultMap，并将解析的结果保存到 resultMappings中
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   * 校验<collection>节点的property属性是否在对应的javaBean中有该字段
   *
   * @param context       封装<<collection>节点的{@link XNode}对象
   * @param enclosingType
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
      && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 绑定Mapper接口
   */
  private void bindMapperForNamespace() {
    // 获取当前映射文件的 namespace
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        // 加载namespace指定的mapper接口
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        // 是否已加载了Mapper接口
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          /*
            Spring可能不知道真正的资源名称，因此我们设置了一个标志防止再次从映射器界面加载此资源
            参考MapperAnnotationBuilder＃loadXmlResource
           */
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
