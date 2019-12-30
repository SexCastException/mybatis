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
package org.apache.ibatis.builder.annotation;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * 注解配置方式Mapper的构建器，XML配置方式 See {@link XMLMapperBuilder}
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class MapperAnnotationBuilder {

  /**
   * 保存{@link Select}、{@link Insert}、{@link Update}和{@link Delete}<br>
   * 相当于Mapper映射文件的<select>、<insert>、<update>和<delete>节点
   */
  private static final Set<Class<? extends Annotation>> SQL_ANNOTATION_TYPES = new HashSet<>();
  private static final Set<Class<? extends Annotation>> SQL_PROVIDER_ANNOTATION_TYPES = new HashSet<>();

  private final Configuration configuration;
  private final MapperBuilderAssistant assistant;
  /**
   * Mapper接口对应的Class对象
   */
  private final Class<?> type;

  static {
    SQL_ANNOTATION_TYPES.add(Select.class);
    SQL_ANNOTATION_TYPES.add(Insert.class);
    SQL_ANNOTATION_TYPES.add(Update.class);
    SQL_ANNOTATION_TYPES.add(Delete.class);

    SQL_PROVIDER_ANNOTATION_TYPES.add(SelectProvider.class);
    SQL_PROVIDER_ANNOTATION_TYPES.add(InsertProvider.class);
    SQL_PROVIDER_ANNOTATION_TYPES.add(UpdateProvider.class);
    SQL_PROVIDER_ANNOTATION_TYPES.add(DeleteProvider.class);
  }

  public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
    // 将Mapper接口的权限定名的“.”用“/”替代
    String resource = type.getName().replace('.', '/') + ".java (best guess)";
    // 初始化每个映射文件对象的 MapperBuilderAssistant 对象
    this.assistant = new MapperBuilderAssistant(configuration, resource);
    this.configuration = configuration;
    this.type = type;
  }

  public void parse() {
    // 格式：interface Mapper接口全限定名
    String resource = type.toString();
    // 判断是否已加载过此资源，加载过则不再加载
    if (!configuration.isResourceLoaded(resource)) {
      loadXmlResource();
      configuration.addLoadedResource(resource);
      assistant.setCurrentNamespace(type.getName());
      // 解析并创建缓存和缓存引用
      parseCache();
      parseCacheRef();
      // 获取Mapper接口的所有方法
      Method[] methods = type.getMethods();
      for (Method method : methods) {
        try {
          // issue #237
          // 不是桥接方法，则解析，主要针对泛型
          if (!method.isBridge()) {
            parseStatement(method);
          }
        } catch (IncompleteElementException e) {
          configuration.addIncompleteMethod(new MethodResolver(this, method));
        }
      }
    }
    parsePendingMethods();
  }

  /**
   * 解析待定方法
   */
  private void parsePendingMethods() {
    Collection<MethodResolver> incompleteMethods = configuration.getIncompleteMethods();
    synchronized (incompleteMethods) {
      Iterator<MethodResolver> iter = incompleteMethods.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // This method is still missing a resource
        }
      }
    }
  }

  private void loadXmlResource() {
    // Spring may not know the real resource name so we check a flag
    // to prevent loading again a resource twice
    // this flag is set at XMLMapperBuilder#bindMapperForNamespace
    /*
      翻译：Spring可能不知道真正的资源名称，所以我们检查一个flag防止再次加载资源两次
      此标志在XMLMapperBuilder＃bindMapperForNamespace设置
     */
    if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
      // 通过Mapper接口权限定名处理后作为对应的Mapper映射文件的资源路径
      String xmlResource = type.getName().replace('.', '/') + ".xml";
      // #1347
      // 读取Mapper映射文件
      InputStream inputStream = type.getResourceAsStream("/" + xmlResource);
      if (inputStream == null) {
        // Search XML mapper that is not in the module but in the classpath.
        // 翻译：搜索不在模块中但在类路径中的XML映射器。
        try {
          // 读取失败，则通过加载Mapper接口的类加载器加载Mapper映射文件
          inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
        } catch (IOException e2) {
          // ignore, resource is not required
        }
      }
      if (inputStream != null) {
        XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
        xmlParser.parse();
      }
    }
  }

  /**
   * 使用注解方式创建缓存。
   * 注解方式优先级大于Mapper映射文件配置缓存方式，因为注解方式创建的缓存后于配置方式创建的缓存，配置方式创建的缓存将被覆盖
   */
  private void parseCache() {
    // 获取Mapper接口的@CacheNamespace注解
    CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
    if (cacheDomain != null) {
      Integer size = cacheDomain.size() == 0 ? null : cacheDomain.size();
      Long flushInterval = cacheDomain.flushInterval() == 0 ? null : cacheDomain.flushInterval();
      // 获取@CacheNamespace注解关于缓存的配置项
      Properties props = convertToProperties(cacheDomain.properties());
      // 创建缓存对象并加入Configuration对象中的caches集合
      assistant.useNewCache(cacheDomain.implementation(), cacheDomain.eviction(), flushInterval, size, cacheDomain.readWrite(), cacheDomain.blocking(), props);
    }
  }

  private Properties convertToProperties(Property[] properties) {
    if (properties.length == 0) {
      return null;
    }
    Properties props = new Properties();
    for (Property property : properties) {
      props.setProperty(property.name(),
        // 值可以使用占位符
        PropertyParser.parse(property.value(), configuration.getVariables()));
    }
    return props;
  }

  /**
   * 注解方式的缓存引用，注解方式优先级大于映射配置方式
   */
  private void parseCacheRef() {
    CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
    if (cacheDomainRef != null) {
      Class<?> refType = cacheDomainRef.value();
      String refName = cacheDomainRef.name();
      // @CacheNamespaceRef注解的value和name字段没有配置，则抛出异常
      if (refType == void.class && refName.isEmpty()) {
        throw new BuilderException("Should be specified either value() or name() attribute in the @CacheNamespaceRef");
      }
      // @CacheNamespaceRef注解的value和name字段都配置了，则抛出异常
      if (refType != void.class && !refName.isEmpty()) {
        throw new BuilderException("Cannot use both value() and name() attribute in the @CacheNamespaceRef");
      }
      String namespace = (refType != void.class) ? refType.getName() : refName;
      try {
        assistant.useCacheRef(namespace);
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(new CacheRefResolver(assistant, namespace));
      }
    }
  }

  private String parseResultMap(Method method) {
    Class<?> returnType = getReturnType(method);

    // 获取Mapper接口方法其他注解
    ConstructorArgs args = method.getAnnotation(ConstructorArgs.class);
    Results results = method.getAnnotation(Results.class);
    TypeDiscriminator typeDiscriminator = method.getAnnotation(TypeDiscriminator.class);

    //
    String resultMapId = generateResultMapName(method);
    applyResultMap(resultMapId, returnType, argsIf(args), resultsIf(results), typeDiscriminator);
    return resultMapId;
  }

  /**
   * 生成ResultMap的名字。
   * <p>
   * 如果添加了注解 {@link Results},则名字由Mapper类型全限定名和注解value决定，否则由Mapper类型全限定名和参数列表决定
   *
   * @param method
   * @return
   */
  private String generateResultMapName(Method method) {
    Results results = method.getAnnotation(Results.class);
    if (results != null && !results.id().isEmpty()) {
      return type.getName() + "." + results.id();
    }
    StringBuilder suffix = new StringBuilder();
    for (Class<?> c : method.getParameterTypes()) {
      suffix.append("-");
      suffix.append(c.getSimpleName());
    }
    if (suffix.length() < 1) {
      suffix.append("-void");
    }
    return type.getName() + "." + method.getName() + suffix;
  }

  private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results, TypeDiscriminator discriminator) {
    List<ResultMapping> resultMappings = new ArrayList<>();
    applyConstructorArgs(args, returnType, resultMappings);
    applyResults(results, returnType, resultMappings);
    Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);
    // TODO add AutoMappingBehaviour
    assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);
    createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
  }

  private void createDiscriminatorResultMaps(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      for (Case c : discriminator.cases()) {
        String caseResultMapId = resultMapId + "-" + c.value();
        List<ResultMapping> resultMappings = new ArrayList<>();
        // issue #136
        applyConstructorArgs(c.constructArgs(), resultType, resultMappings);
        applyResults(c.results(), resultType, resultMappings);
        // TODO add AutoMappingBehaviour
        assistant.addResultMap(caseResultMapId, c.type(), resultMapId, null, resultMappings, null);
      }
    }
  }

  private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      String column = discriminator.column();
      Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
      JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        (discriminator.typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler());
      Case[] cases = discriminator.cases();
      Map<String, String> discriminatorMap = new HashMap<>();
      for (Case c : cases) {
        String value = c.value();
        String caseResultMapId = resultMapId + "-" + value;
        discriminatorMap.put(value, caseResultMapId);
      }
      return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
    }
    return null;
  }

  /**
   * @param method
   */
  void parseStatement(Method method) {
    // 获取Mapper接口方法的形参类型
    Class<?> parameterTypeClass = getParameterType(method);
    // 获取Mapper接口方法使用@Lang注解指定的LanguageDriver对象
    LanguageDriver languageDriver = getLanguageDriver(method);
    // 根据注解配置信息创建SqlSource
    SqlSource sqlSource = getSqlSourceFromAnnotations(method, parameterTypeClass, languageDriver);
    if (sqlSource != null) {
      // 获取Mapper接口方法的@Options注解
      Options options = method.getAnnotation(Options.class);
      // 拼接mappedStatementId，等价于映射文件的命名空间+Statement节点的id
      final String mappedStatementId = type.getName() + "." + method.getName();
      Integer fetchSize = null;
      Integer timeout = null;
      StatementType statementType = StatementType.PREPARED;
      ResultSetType resultSetType = configuration.getDefaultResultSetType();
      // 获取Mapper接口方法SQL语句的类型
      SqlCommandType sqlCommandType = getSqlCommandType(method);
      // 是否为查询语句
      boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
      // 查询语句默认不刷讯缓存
      boolean flushCache = !isSelect;
      // 查询语句默认使用缓存
      boolean useCache = isSelect;

      KeyGenerator keyGenerator;
      String keyProperty = null;
      String keyColumn = null;
      // SQL语句为insert或update类型
      if (SqlCommandType.INSERT.equals(sqlCommandType) || SqlCommandType.UPDATE.equals(sqlCommandType)) {
        // first check for SelectKey annotation - that overrides everything else
        // 获取Mapper方法接口上的SelectKey注解
        SelectKey selectKey = method.getAnnotation(SelectKey.class);
        if (selectKey != null) {
          // 处理@selectKey注解
          keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method), languageDriver);
          keyProperty = selectKey.keyProperty();
        } else if (options == null) {
          keyGenerator = configuration.isUseGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
        } else {
          keyGenerator = options.useGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
          keyProperty = options.keyProperty();
          keyColumn = options.keyColumn();
        }
      } else {
        keyGenerator = NoKeyGenerator.INSTANCE;
      }

      // 获取@Options注解其他相关配置项
      if (options != null) {
        if (FlushCachePolicy.TRUE.equals(options.flushCache())) {
          flushCache = true;
        } else if (FlushCachePolicy.FALSE.equals(options.flushCache())) {
          flushCache = false;
        }
        useCache = options.useCache();
        fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null; //issue #348
        timeout = options.timeout() > -1 ? options.timeout() : null;
        statementType = options.statementType();
        if (options.resultSetType() != ResultSetType.DEFAULT) {
          resultSetType = options.resultSetType();
        }
      }

      String resultMapId = null;
      // 获取@ResultMap注解
      ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
      if (resultMapAnnotation != null) {
        resultMapId = String.join(",", resultMapAnnotation.value());
      } else if (isSelect) {
        resultMapId = parseResultMap(method);
      }

      assistant.addMappedStatement(
        mappedStatementId,
        sqlSource,
        statementType,
        sqlCommandType,
        fetchSize,
        timeout,
        // ParameterMapID
        null,
        parameterTypeClass,
        resultMapId,
        getReturnType(method),
        resultSetType,
        flushCache,
        useCache,
        // TODO gcode issue #577
        false,
        keyGenerator,
        keyProperty,
        keyColumn,
        // DatabaseID
        null,
        languageDriver,
        // ResultSets
        options != null ? nullOrEmpty(options.resultSets()) : null);
    }
  }

  /**
   * 获取Mapper接口方法上使用{@link Lang}注解指定的{@link LanguageDriver}对象，不指定则返回默认的
   *
   * @param method
   * @return
   */
  private LanguageDriver getLanguageDriver(Method method) {
    Lang lang = method.getAnnotation(Lang.class);
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      langClass = lang.value();
    }
    return configuration.getLanguageDriver(langClass);
  }

  /**
   * 返回除{@link RowBounds}和{@link ResultHandler}之外的Mapper接口方法形参类型，多于一个形参则返回{@link ParamMap}类型
   *
   * @param method
   * @return
   */
  private Class<?> getParameterType(Method method) {
    Class<?> parameterType = null;
    // 获取Mapper接口方法上的形参类型
    Class<?>[] parameterTypes = method.getParameterTypes();
    for (Class<?> currentParameterType : parameterTypes) {
      // 不返回形参类型为RowBounds和ResultHandler子类的形参
      if (!RowBounds.class.isAssignableFrom(currentParameterType) && !ResultHandler.class.isAssignableFrom(currentParameterType)) {
        // 除RowBounds和ResultHandler外，如果只配置了一个形参，则原样返回，否则返回ParamMap类型，本质为HashMap
        if (parameterType == null) {
          parameterType = currentParameterType;
        } else {
          // issue #135
          parameterType = ParamMap.class;
        }
      }
    }
    return parameterType;
  }

  /**
   * 根据Mapper接口方法注解返回合适的返回值类型
   *
   * @param method
   * @return
   */
  private Class<?> getReturnType(Method method) {
    // 获取方法返回值
    Class<?> returnType = method.getReturnType();
    Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, type);
    // 如果返回值是普通java类型
    if (resolvedReturnType instanceof Class) {
      returnType = (Class<?>) resolvedReturnType;
      // 如果是数组，则返回值类型是数据组件的类型
      if (returnType.isArray()) {
        returnType = returnType.getComponentType();
      }
      // gcode issue #508
      // 如果返回值是void类型，
      if (void.class.equals(returnType)) {
        ResultType rt = method.getAnnotation(ResultType.class);
        if (rt != null) {
          returnType = rt.value();
        }
      }
    } else if (resolvedReturnType instanceof ParameterizedType) { // 如果返回值是泛型
      ParameterizedType parameterizedType = (ParameterizedType) resolvedReturnType;
      // 获取泛型原始类型，如：List
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      // 泛型的原始类型是Collection（集合）或Cursor（游标）
      if (Collection.class.isAssignableFrom(rawType) || Cursor.class.isAssignableFrom(rawType)) {
        // 获取泛型类型参数
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        // 只识别带一个类型参数的泛型
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          Type returnTypeParameter = actualTypeArguments[0];
          if (returnTypeParameter instanceof Class<?>) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {  // 如果参数类型还是泛型
            // (gcode issue #443) actual type can be a also a parameterized type
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          } else if (returnTypeParameter instanceof GenericArrayType) { // 如果是泛型数组或类型变量数组
            // 返回数组组件的类型
            Class<?> componentType = (Class<?>) ((GenericArrayType) returnTypeParameter).getGenericComponentType();
            // (gcode issue #525) support List<byte[]>
            returnType = Array.newInstance(componentType, 0).getClass();
          }
        }

        // 如果Mapper接口方法有注解MapKey并且返回值类型是Map
      } else if (method.isAnnotationPresent(MapKey.class) && Map.class.isAssignableFrom(rawType)) {
        // (gcode issue 504) Do not look into Maps if there is not MapKey annotation
        // 获取Map的实际类型参数
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 2) {
          // 获取Map的value值类型
          Type returnTypeParameter = actualTypeArguments[1];
          if (returnTypeParameter instanceof Class<?>) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {
            // (gcode issue 443) actual type can be a also a parameterized type
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          }
        }
      } else if (Optional.class.equals(rawType)) {  // 如果Optional，则直接返回类型参数
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type returnTypeParameter = actualTypeArguments[0];
        if (returnTypeParameter instanceof Class<?>) {
          returnType = (Class<?>) returnTypeParameter;
        }
      }
    }

    return returnType;
  }

  /**
   * 根据注解上配置的信息创建 {@link SqlSource}
   *
   * @param method
   * @param parameterType
   * @param languageDriver
   * @return
   */
  private SqlSource getSqlSourceFromAnnotations(Method method, Class<?> parameterType, LanguageDriver languageDriver) {
    try {
      // 获取Mapper接口方法上指定的注解（@Select、@Insert、@Update或@Delete）
      Class<? extends Annotation> sqlAnnotationType = getSqlAnnotationType(method);
      // 获取Mapper接口方法上指定的注解（@SelectProvider、@InsertProvider、@UpdateProvider或@DeleteProvider）
      Class<? extends Annotation> sqlProviderAnnotationType = getSqlProviderAnnotationType(method);
      /*
        Mapper接口方法不能同时指定sqlAnnotationType和sqlProviderAnnotationType
        如指定了@Select注解就不能使用@SelectProvider注解
       */
      if (sqlAnnotationType != null) {
        if (sqlProviderAnnotationType != null) {
          throw new BindingException("You cannot supply both a static SQL and SqlProvider to method named " + method.getName());
        }
        Annotation sqlAnnotation = method.getAnnotation(sqlAnnotationType);
        // 通过反射获取sqlAnnotationType注解的value值
        final String[] strings = (String[]) sqlAnnotation.getClass().getMethod("value").invoke(sqlAnnotation);
        // 从注解上value指定的字符串数组创建SqlSource对象
        return buildSqlSourceFromStrings(strings, parameterType, languageDriver);
      } else if (sqlProviderAnnotationType != null) {
        Annotation sqlProviderAnnotation = method.getAnnotation(sqlProviderAnnotationType);
        return new ProviderSqlSource(assistant.getConfiguration(), sqlProviderAnnotation, type, method);
      }
      return null;
    } catch (Exception e) {
      throw new BuilderException("Could not find value method on SQL annotation.  Cause: " + e, e);
    }
  }

  private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    final StringBuilder sql = new StringBuilder();
    // 拼接SQL语句
    for (String fragment : strings) {
      sql.append(fragment);
      sql.append(" ");
    }
    return languageDriver.createSqlSource(configuration, sql.toString().trim(), parameterTypeClass);
  }

  /**
   * 根据Mapper接口方法的注解判断SQL语句的类型
   *
   * @param method
   * @return
   */
  private SqlCommandType getSqlCommandType(Method method) {
    // 获取Mapper接口方法的注解
    Class<? extends Annotation> type = getSqlAnnotationType(method);

    if (type == null) {
      type = getSqlProviderAnnotationType(method);

      if (type == null) {
        return SqlCommandType.UNKNOWN;
      }

      if (type == SelectProvider.class) {
        type = Select.class;
      } else if (type == InsertProvider.class) {
        type = Insert.class;
      } else if (type == UpdateProvider.class) {
        type = Update.class;
      } else if (type == DeleteProvider.class) {
        type = Delete.class;
      }
    }

    return SqlCommandType.valueOf(type.getSimpleName().toUpperCase(Locale.ENGLISH));
  }

  private Class<? extends Annotation> getSqlAnnotationType(Method method) {
    return chooseAnnotationType(method, SQL_ANNOTATION_TYPES);
  }

  private Class<? extends Annotation> getSqlProviderAnnotationType(Method method) {
    return chooseAnnotationType(method, SQL_PROVIDER_ANNOTATION_TYPES);
  }

  /**
   * 选择在types注解集合中被method使用的注解类型，指定多个则只返回第一个
   *
   * @param method
   * @param types
   * @return
   */
  private Class<? extends Annotation> chooseAnnotationType(Method method, Set<Class<? extends Annotation>> types) {
    for (Class<? extends Annotation> type : types) {
      Annotation annotation = method.getAnnotation(type);
      if (annotation != null) {
        return type;
      }
    }
    return null;
  }

  private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Result result : results) {
      List<ResultFlag> flags = new ArrayList<>();
      if (result.id()) {
        flags.add(ResultFlag.ID);
      }
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        ((result.typeHandler() == UnknownTypeHandler.class) ? null : result.typeHandler());
      ResultMapping resultMapping = assistant.buildResultMapping(
        resultType,
        nullOrEmpty(result.property()),
        nullOrEmpty(result.column()),
        result.javaType() == void.class ? null : result.javaType(),
        result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
        hasNestedSelect(result) ? nestedSelectId(result) : null,
        null,
        null,
        null,
        typeHandler,
        flags,
        null,
        null,
        isLazy(result));
      resultMappings.add(resultMapping);
    }
  }

  private String nestedSelectId(Result result) {
    String nestedSelect = result.one().select();
    if (nestedSelect.length() < 1) {
      nestedSelect = result.many().select();
    }
    if (!nestedSelect.contains(".")) {
      nestedSelect = type.getName() + "." + nestedSelect;
    }
    return nestedSelect;
  }

  private boolean isLazy(Result result) {
    boolean isLazy = configuration.isLazyLoadingEnabled();
    if (result.one().select().length() > 0 && FetchType.DEFAULT != result.one().fetchType()) {
      isLazy = result.one().fetchType() == FetchType.LAZY;
    } else if (result.many().select().length() > 0 && FetchType.DEFAULT != result.many().fetchType()) {
      isLazy = result.many().fetchType() == FetchType.LAZY;
    }
    return isLazy;
  }

  private boolean hasNestedSelect(Result result) {
    if (result.one().select().length() > 0 && result.many().select().length() > 0) {
      throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
    }
    return result.one().select().length() > 0 || result.many().select().length() > 0;
  }

  private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Arg arg : args) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if (arg.id()) {
        flags.add(ResultFlag.ID);
      }
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        (arg.typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler());
      ResultMapping resultMapping = assistant.buildResultMapping(
        resultType,
        nullOrEmpty(arg.name()),
        nullOrEmpty(arg.column()),
        arg.javaType() == void.class ? null : arg.javaType(),
        arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(),
        nullOrEmpty(arg.select()),
        nullOrEmpty(arg.resultMap()),
        null,
        nullOrEmpty(arg.columnPrefix()),
        typeHandler,
        flags,
        null,
        null,
        false);
      resultMappings.add(resultMapping);
    }
  }

  private String nullOrEmpty(String value) {
    return value == null || value.trim().length() == 0 ? null : value;
  }

  private Result[] resultsIf(Results results) {
    return results == null ? new Result[0] : results.value();
  }

  private Arg[] argsIf(ConstructorArgs args) {
    return args == null ? new Arg[0] : args.value();
  }

  private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    // 拼接selectKey的命名完整命名
    String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;

    // 获取@selectKey注解相关配置
    Class<?> resultTypeClass = selectKeyAnnotation.resultType();
    StatementType statementType = selectKeyAnnotation.statementType();
    String keyProperty = selectKeyAnnotation.keyProperty();
    String keyColumn = selectKeyAnnotation.keyColumn();
    boolean executeBefore = selectKeyAnnotation.before();

    // defaults
    boolean useCache = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    // 根据selectKey语句创建对应的SqlSource对象
    SqlSource sqlSource = buildSqlSourceFromStrings(selectKeyAnnotation.statement(), parameterTypeClass, languageDriver);
    // selectKey语句默认为查询语句
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
      flushCache, useCache, false,
      keyGenerator, keyProperty, keyColumn, null, languageDriver, null);

    id = assistant.applyCurrentNamespace(id, false);

    // 从Configuration对象中获取addMappedStatement() 方法生成的MappedStatement对象
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
    configuration.addKeyGenerator(id, answer);
    return answer;
  }

}
