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

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * mybatis-config.xml配置文件构建器
 * <p>
 * 解析mybatis-config.xml配置文件创建{@link Configuration}
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 标志是否被解析过配置文件，解析过则不再解析
   */
  private boolean parsed;
  private final XPathParser parser;
  /**
   * 标识<environment>的名称，默认读取<environment>标签的default属性
   */
  private String environment;
  /**
   * 负责创建和缓存{@link org.apache.ibatis.reflection.Reflector} 对象
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  /**
   * @param reader      封装xml配置文件的字符流对象
   * @param environment
   * @param props
   */
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  /**
   * @param inputStream 封装xml配置文件的字节流对象
   * @param environment
   * @param props
   */
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * 此类有多个重载的构造函数，最终都会调用到该私有的构造函数
   *
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // 配置文件对象对象在此实例化
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 将配置信息设置到Configuration对象的variables属性
    this.configuration.setVariables(props);
    // 默认未解析过配置文件
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析配置文件创建{@link Configuration}，是解析mybatis-config.xml的入口
   *
   * @return
   */
  public Configuration parse() {
    // 每个XMLConfigBuilder只能解析一次配置文件，超过一次抛出异常
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 配置文件修改为解析
    parsed = true;
    // 从<configuration><configuration/>根节点开始解析
    // 解析配置文件根节点以及所有子节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析根节点时，把所有子节点都解析出来，解析子节点的时候严格按照一定的顺序，因为有些配置需要到前面的配置项
   *
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // 解析<properties>节点
      propertiesElement(root.evalNode("properties"));
      // 解析<settings>节点
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);  // 使用<settings>加载用户自定义的VFS对象实现类，配置name为：vfsImpl
      loadCustomLogImpl(settings);  // 使用<settings>加载用户自定义的Log实现类对象，配置name为：logImpl
      // 解析<typeAliases>节点
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析<plugins>节点
      pluginElement(root.evalNode("plugins"));
      // 解析<objectFactory>节点，流程和pluginElement()方法类似
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析<objectWrapperFactory>节点，流程和pluginElement()方法类似
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析<reflectorFactory>节点，流程和pluginElement()方法类似
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);  // 将settings配置初始化Configuration字段
      // read it after objectFactory and objectWrapperFactory issue #631

      // 解析<environments>节点
      environmentsElement(root.evalNode("environments"));
      // 解析<databaseIdProvider>节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析<typeHandlers>节点
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析<mappers>节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 解析mybatis-config.xml的<settings>节点，该节点是mybatis全局性的配置，他们会改变mybatis的运行行为，这些配置信息都会记录到Configuration的字段中
   *
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 解析<settings>的子节点(<setting>标签)的name和value属性，并返回Properties对象
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 创建Configuration的MetaClass对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      // 检测<setting>标签配置的key属性在Configuration中是否有对象的字段，没有则抛出异常
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 将<setting>节点name值为vfsImpl的值（多个值用“,”隔开）用类加载器加载生成对应的Class对象
   * 并更新Configuration中的{@link Configuration#vfsImpl}字段和VFS中的{@link VFS#USER_IMPLEMENTATIONS}的集合
   *
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          // 使用类加载加载指定的类
            Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 注册别名
   *
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 通过package批量注册别名，优先级最高
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 通过typeAlias标签alias属性和type属性注册别名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              // 扫描@Alias注解注册别名
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 注册插件，interceptor属性可以指定别名
   *
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历<plugins>的子标签<plugin>
      for (XNode child : parent.getChildren()) {
        // 获取<plugin>属性interceptor的值
        String interceptor = child.getStringAttribute("interceptor");
        // 获取封装<plugin>子标签<property>的属性name 和value的Properties对象
        Properties properties = child.getChildrenAsProperties();
        // 通过interceptor属性配置的值解析Class对象（支持别名），并通过默认构造器实例化Interceptor对象
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 将子标签<property>的信息赋值到Interceptor对象的配置中去
        interceptorInstance.setProperties(properties);
        // Configuration全局配置文件中添加Interceptor对象
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取<objectFactory>标签的type属性值
      String type = context.getStringAttribute("type");
      // 获取封装<objectFactory>标签的子标签<property>name和value的Properties对象
      Properties properties = context.getChildrenAsProperties();
      // 通过type配置的属性值获取（优先从TypeAliasRegistry获取） ObjectFactory对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 将<property>的封装的配置信设置到ObjectFactory对象中
      factory.setProperties(properties);
      // 更新Configuration的objectFactory字段
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 解析<objectWrapperFactory>标签的type属性值
      String type = context.getStringAttribute("type");
      // 通过type配置的属性值获取（优先从TypeAliasRegistry获取） ObjectWrapperFactory 对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 更新Configuration中的 objectWrapperFactory 字段
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 解析<reflectorFactory>标签的type属性值
      String type = context.getStringAttribute("type");
      // 通过type配置的属性值获取（优先从TypeAliasRegistry获取） ReflectorFactory 对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 更新Configuration中的 reflectorFactory 字段
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析mybatis-config.xml配置文件中的<properties>节点并形成{@link Properties} 对象，之后将该对象设置到{@link XPathParser} 和
   * {@link Configuration}中的variables属性去，在后面的解析中，会使用{@link Properties}对象的信息替换占位符“${}”里的值。<br><br>
   * <p>
   * 优先级：resource > url
   *
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 解析<properties>子标签<property>的name和value属性，并封装成Properties对象
      Properties defaults = context.getChildrenAsProperties();
      // 解析<properties>的resource和url属性，这两个属性用于确定properties配置文件的位置
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // resource和url不能同时存在，否则抛出异常
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 将解析出来的属性与Configuration中的variables属性合并
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 合并后的属性更新XPathParse和Configuration中的variables字段
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 根据配置信息更新{@link Configuration}相应的字段，没有配置的，则赋予相应的默认值
   *
   * @param props
   */
  private void settingsElement(Properties props) {
    // 默认部分自动映射
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    // 默认开启一级缓存功能
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    // 默认关闭懒加载功能
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    // 默认关闭积懒加载功能
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    // 默认为简单执行器
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    // 如果开启了懒加载功能，默认触发加载的函数，equals、clone、hashCode和toString
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 在实际生产中，同一项目可能分为开发、测试和生产多个不同的环境，每个环境的配置可能也不尽相同。
   * MyBatis可以配置多个<environment>节点，每个<environment>节点对应一种环境的配置。
   * 但需要注意的是，尽管可以配置多个环境，每个 {@link SqlSessionFactory} 实例只能选择其一
   * <p>
   * XMLConfigBuilder.environmentsElement()方法负责解析<environments>的相关配置，它会根
   * 据 XMLConfigBuilder.environment 字段值确定要使用的<environment>配置，之后创建对应的
   * {@link TransactionFactory}和{@link DataSource}对象，并封装进{@link Environment}对象中。
   *
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      // 未指定environment字段，则使用default属性指定的<environment>
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      // 遍历<environments>的子标签<environment>
      for (XNode child : context.getChildren()) {
        // 获取标签<environment>的id属性值
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) { // 是否与XMLConfigBuilder.environment 字段匹配，从多数据源中查找需要配置的Environment配置信息
          // 解析<transactionManager>配置的信息获取 TransactionFactory 对象
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 解析<dataSource>配置的信息获取 DataSourceFactory 对象
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          // 获取数据源
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
            .transactionFactory(txFactory)
            .dataSource(dataSource);
          // 更新Configuration的environment字段
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * MyBatis不能像Hibernate那样,直接帮助开发人员屏蔽多种数据库产品在SQL语言支持方面的差异。
   * 但是在mybatis-config.xml配置文件中，通过<databaseIdProvider>定义所有支持的数据库产品的 databaseId,
   * 然后在映射配置文件中定义SQL语句节点时，通过 databaseId 指定该SQL语句应用的数据库产品，这样也可以实现类似的功能。
   * <p>
   * 在MyBatis初始化时，会根据前面确定的{@link DataSource}确定当前使用的数据库产品，然后在解析映射配置文件时，
   * 加载不带 databaseId 属性和带有匹配当前数据库 databaseId 属性的所有SQL语句。
   * 如果同时找到带有 databaseId 和不带 databaseId 的相同语句，则后者会被舍弃，使用前者。
   * <p>
   * XMLConfigBuilder.databaseIdProviderElement()方法负责解析<databaseIdProvider>节点，并创建指定的DatabaseIdProvider对象。
   * DatabaseIdProvider 会返回 databaseId 值，MyBatis 会根据databaseId 选择合适的SQL进行执行。
   *
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      // 获取<databaseIdProvider>标签的type属性值
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      // 翻译：糟糕的补丁以保持向后兼容性
      if ("VENDOR".equals(type)) {  // 兼容性处理
        type = "DB_VENDOR";
      }
      // 获取封装<databaseIdProvider>的子标签<property>属性name和value的Properties对象
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      // 将<property>属性信息赋值到DatabaseIdProvider中，即与数据库产品名称匹配的配置项
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      // 通过前面确定的DataSource获取databaseId,并记录到Configuration.databaseId字段中
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 解析<transactionManager>标签
   *
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获取<transactionManager>的type属性值
      String type = context.getStringAttribute("type");
      // 获取封装<transactionManager>标签的子标签<property>的name和value的Properties对象
      Properties props = context.getChildrenAsProperties();
      // 通过type配置的属性值获取（优先从TypeAliasRegistry获取） TransactionFactory 对象
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 将<property>的配置信息赋值到 TransactionFactory 对象中
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 解析<environment>子节点<dataSource>的type属性值，该值指定了使用数据源的类型
      String type = context.getStringAttribute("type");
      // 解析<dataSource>节点子节点<property>的配置信息，该配置信息为数据源初始化的配置，包含数据库账号密码，连接超时时间、url等等
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析 <typeHandlers> 标签
   *
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      // 遍历<typeHandlers>的子标签（<package>和<typeHandler>），优先级：package > typeHandler
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          // 获取<package>的属性name指定的包名
          String typeHandlerPackage = child.getStringAttribute("name");
          // 扫描包注册TypeHandler
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          // 获取<typeHandler>属性 javaType 的值
          String javaTypeName = child.getStringAttribute("javaType");
          // 获取<typeHandler>属性 jdbcType 的值
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          // 获取<typeHandler>属性 handler 的值
          String handlerTypeName = child.getStringAttribute("handler");
          // 通过属性handler指定的别名获取相应的类全限定名
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          // 通过以上配置的信息调用相应的方法注册 TypeHandler
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 在MyBatis初始化时，除了加载mybatis-config.xml配置文件，还会加载全部的映射配置文件，mybatis-config.xml配置文件
   * 中的<mappers>节点会告诉MyBatis去哪些位置查找映射配置文件以及使用了配置注解标识的接口。
   * <p>
   * 此负责解析<mappers>节点，它会创建 XMLConfigBuilder 对象加载映射文件，如果映射配置文件存在相应的Mapper接口，也会加载
   * 相应的Mapper接口，解析其中的注解并完成向{@link MapperRegistry}的注册。
   *
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历<mappers>标签下所有子标签（<mapper>和<package>），优先级：package > resource > url > class，面试官可能会问
      for (XNode child : parent.getChildren()) {
        // 解析<mappers>标签的子标签<package>
        if ("package".equals(child.getName())) {
          // 获取<package>标签的name属性值
          String mapperPackage = child.getStringAttribute("name");
          // ／扫描指定的包，并向 MapperRegistry 注册 Mapper 接口
          configuration.addMappers(mapperPackage);
        } else {  // 解析<mappers>标签的子标签<mapper>
          // 获取<mappers>标签resource属性值
          String resource = child.getStringAttribute("resource");
          // 获取<mappers>标签url属性值
          String url = child.getStringAttribute("url");
          // 获取<mappers>标签class属性值
          String mapperClass = child.getStringAttribute("class");
          // 如果<mapper>节点指定了resource或是url属性,则创建XMLMapperBuilder对象，并通过该对象解析resource或是url属性指定的Mapper配置文件
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            // 将resource属性指定的资源路劲通过类加载器加载并返回资源输入流对象
            InputStream inputStream = Resources.getResourceAsStream(resource);
            // 创建 XMLMapperBuilder 对象，XMLMapperBuilder对象在此创建
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            // 将url属性指定的资源路劲通过类加载器加载并返回资源输入流对象
            InputStream inputStream = Resources.getUrlAsStream(url);
            // 创建 XMLMapperBuilder 对象，XMLMapperBuilder对象在此创建
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            // 将class属性指定的类全限定名通过类加载器并返回相应Mapper 的 Class对象
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            // 同一个<mapper>不能同时配置resource、url和class，否则将抛出此异常
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   * <environment>的id属性值是否为原来environment已经指定的值
   *
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
