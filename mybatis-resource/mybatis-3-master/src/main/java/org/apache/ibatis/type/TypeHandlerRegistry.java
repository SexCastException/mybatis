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
package org.apache.ibatis.type;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.chrono.JapaneseDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在MyBatis 初始化过程中，会为所有已知的${@link TypeHandler} 创建对象，并实现注册到 TypeHandlerRegistry中，
 * 由TypeHandlerRegistry负责管理这些TypeHandler对象。TypeHandlerRegistry是维护javaType和jdbcType关系映射的注册中心。
 * <p>
 * javaType和jdbcType，一对多，比如{@link java.lang.String} 对应数据库的JdbcType的CHAR和VARCHAR等，CHAR只对应这javaType的{@link java.lang.String}
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

  /**
   * 记录JdbcType与TypeHandler之间的对应关系，其中JdbcType是一个枚举类型，它定义对应的JDBC类型
   * 该集合主要用于从结果集读取数据时，将数据从Jdbc类型转换成Java类型
   */
  private final Map<JdbcType, TypeHandler<?>> jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);

  /**
   * 记录了Java类型向指定JdbcType转换时，需要使用的TypeHandler对象。例如: Java类型中的String可能
   * 转换成数据库的char、varchar等多种类型，所以存在一对多关系
   * <p>
   * 键为java类型
   */
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
  /**
   * 未知类型处理器
   */
  private final TypeHandler<Object> unknownTypeHandler = new UnknownTypeHandler(this);

  /**
   * 记录了全部TypeHandler的类型以及该类型相应的TypeHandler对象，键为TypeHandler的Class对象，值为TypeHandler
   */
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();

  /**
   * 空TypeHandler集合的标识
   */
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  public TypeHandlerRegistry() {
    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());
    register(String.class, new StringTypeHandler());
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   *
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  /**
   * 根据type获取jdbcHandlerMap，如果jdbcType不为空，在从jdbcHandlerMap获取具体的TypeHandler，
   * 如果jdbcType为空且jdbcHandlerMap只有一个元素，则返回该元素，否则返回null
   *
   * @param type
   * @param jdbcType
   * @param <T>
   * @return
   */
  @SuppressWarnings("unchecked")
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    if (ParamMap.class.equals(type)) {
      return null;
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;
    if (jdbcHandlerMap != null) {
      handler = jdbcHandlerMap.get(jdbcType);
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }
      if (handler == null) {
        // #591
        // 获取唯一的TypeHandler对象
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    return (TypeHandler<T>) handler;
  }

  /**
   * 检测 NULL_TYPE_HANDLER_MAP 集合中指定Java类型对应的TypeHandler集合是否已经初始化。如果未初始化，则尝试以该Java类型的、
   * 已初始化的父类对应的TypeHandler 集合为初始集合;
   * <p>
   * 如不存在己初始化的父类，则将其对应的TypeHandler集合初始化为NULL_TYPE_HANDLER_MAP标识，对于枚举类型初始化为 defaultEnumTypeHandler
   *
   * @param type
   * @return
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    // 根据java类型获取具体的jdbcHandlerMap
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    // 检测是否为空集合标识
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
      return null;
    }
    // 获取的jdbcHandlerMap为空则初始化指定的Java类型TypeHandler集合
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      // 如果clazz为枚举类型
      if (Enum.class.isAssignableFrom(clazz)) {
        // 如果clazz 为匿名内部类，则获取父类
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        // 如果
        if (jdbcHandlerMap == null) {
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {
        // 查找父类对应的TypeHandler集合，并作为初始集合
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  /**
   * 根据clazz的父接口获取 jdbcHandlerMap，并使用enumClazz作为构造函数参数新建一个TypeHandler
   *
   * @param clazz
   * @param enumClazz
   * @return
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    for (Class<?> iface : clazz.getInterfaces()) {
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      // 递归直至查找到clazz有父接口已经在typeHandlerMap注册了TypeHandler为止
      if (jdbcHandlerMap == null) {
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          // 使用枚举类型作为构造函数参数创建一个typeHanlder
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        return newMap;
      }
    }
    return null;
  }

  /**
   * 根据clazz的父类获取 jdbcHandlerMap，如果clazz不存在除了Object的父类，则返回null
   *
   * @param clazz
   * @return
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass = clazz.getSuperclass();
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      // 递归直至查找到clazz有父类已经在typeHandlerMap注册了TypeHandler为止
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  /**
   * 选择唯一的handler，如果jdbcHandlerMap有一个（或多个相同类型）元素，则返回此对象，否则返回null
   *
   * @param jdbcHandlerMap
   * @return
   */
  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered.
        // 超过一个 TypeHandlers 则返回null
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }

  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  /**
   * register重载方法1
   *
   * @param typeHandlerClass
   */
  public void register(Class<?> typeHandlerClass) {
    // 是否映射成功
    boolean mappedTypeFound = false;
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      // 根据@MappedTypes注解中指定的Java类型进行注册
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        // 经过强制类型转换以及使用反射创建TypeHandler对象之后，交由register重载方法3继续处理
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    // 以上操作未映射成功，交由register重载方法2处理
    if (!mappedTypeFound) {
      register(getInstance(null, typeHandlerClass));
    }
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  /**
   * 根据注解 @MappedTypes指定的Class进行注册
   * register 重载方法2
   *
   * @param typeHandler
   * @param <T>
   */
  @SuppressWarnings("unchecked")
  public <T> void register(TypeHandler<T> typeHandler) {
    // 是否映射成功
    boolean mappedTypeFound = false;
    // 获取 @MappedTypes 注解
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      // 根据@MappedTypes注解中指定的Java类型进行注册
      for (Class<?> handledType : mappedTypes.value()) {
        // 强制类型转换以及反射创建TypeHandler对象之后，交由 register重载方法3处理
        register(handledType, typeHandler);
        mappedTypeFound = true;
      }
    }
    // @since 3.1.0 - try to auto-discover the mapped type
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        register(typeReference.getRawType(), typeHandler);
        mappedTypeFound = true;
      } catch (Throwable t) {
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    if (!mappedTypeFound) {
      // 未指定@MappedTypes注解，交由 register重载方法2处理
      register((Class<T>) null, typeHandler);
    }
  }

  // java type + handler

  /**
   * register 重载方法，对外公开
   *
   * @param javaType
   * @param typeHandler
   * @param <T>
   */
  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }

  /**
   * register重载方法3，两个形参，jdbcType由MappedJdbcTypes注解获取
   *
   * @param javaType
   * @param typeHandler
   * @param <T>
   */
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    // 获取 MappedJdbcTypes 注解
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
    if (mappedJdbcTypes != null) {
      // 根据@MappedTypes注解中指定的JDBC类型进行注册
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        // 交由register重载方法4处理
        register(javaType, handledJdbcType, typeHandler);
      }
      if (mappedJdbcTypes.includeNullJdbcType()) {
        register(javaType, null, typeHandler);
      }
    } else {
      register(javaType, null, typeHandler);
    }
  }

  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  /**
   * 向typeHandlerMap注册TypeHandler
   *
   * @param javaType
   * @param jdbcType
   * @param handler
   */
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) {
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
      // 不存在则创建新的map并加入到typeHandlerMap中
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
        typeHandlerMap.put(javaType, map);
      }
      // 新加入或覆盖原有的TypeHandler
      map.put(jdbcType, handler);
    }
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  // java type + handler type

  /**
   * 通过类加载器加载指定的 javaTypeClassName 和 typeHandlerClassName 注册
   *
   * @param javaTypeClassName
   * @param typeHandlerClassName
   * @throws ClassNotFoundException
   */
  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)

  /**
   * 通过typeHandler类型创建一个对应的TypeHandler对象，如果允许javaTypeClass 作为构造函数的形参
   *
   * @param javaTypeClass    映射文件javaType属性值对应的Class对象
   * @param typeHandlerClass 映射文件typeHandler属性值对应的Class对象
   * @param <T>
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    if (javaTypeClass != null) {
      try {
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan

  /**
   * 通过扫描包注册TypeHandler
   * register重载方法6
   *
   * @param packageName 包名
   */
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 查找指定包下的 TypeHandler 接口实现类
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      // 过滤内部类、接口和抽象类
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        // 交由register重载方法1处理
        register(type);
      }
    }
  }

  // get information

  /**
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
