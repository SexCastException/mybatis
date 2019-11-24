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

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.*;

/**
 * 在编写SQL语句时，使用别名可以方便理解以及维护，例如表名或列名很长时，我们一般会为其设计易懂易维护的别名。
 * <p>
 * 类型别名注册器
 *
 * @author Clinton Begin
 */
public class TypeAliasRegistry {

  /**
   * 管理别名和java类型之间的映射
   */
  private final Map<String, Class<?>> typeAliases = new HashMap<>();

  public TypeAliasRegistry() {
    // mybatis默认支持的别名
    registerAlias("string", String.class);

    registerAlias("byte", Byte.class);
    registerAlias("long", Long.class);
    registerAlias("short", Short.class);
    registerAlias("int", Integer.class);
    registerAlias("integer", Integer.class);
    registerAlias("double", Double.class);
    registerAlias("float", Float.class);
    registerAlias("boolean", Boolean.class);

    registerAlias("byte[]", Byte[].class);
    registerAlias("long[]", Long[].class);
    registerAlias("short[]", Short[].class);
    registerAlias("int[]", Integer[].class);
    registerAlias("integer[]", Integer[].class);
    registerAlias("double[]", Double[].class);
    registerAlias("float[]", Float[].class);
    registerAlias("boolean[]", Boolean[].class);

    registerAlias("_byte", byte.class);
    registerAlias("_long", long.class);
    registerAlias("_short", short.class);
    registerAlias("_int", int.class);
    registerAlias("_integer", int.class);
    registerAlias("_double", double.class);
    registerAlias("_float", float.class);
    registerAlias("_boolean", boolean.class);

    registerAlias("_byte[]", byte[].class);
    registerAlias("_long[]", long[].class);
    registerAlias("_short[]", short[].class);
    registerAlias("_int[]", int[].class);
    registerAlias("_integer[]", int[].class);
    registerAlias("_double[]", double[].class);
    registerAlias("_float[]", float[].class);
    registerAlias("_boolean[]", boolean[].class);

    registerAlias("date", Date.class);
    registerAlias("decimal", BigDecimal.class);
    registerAlias("bigdecimal", BigDecimal.class);
    registerAlias("biginteger", BigInteger.class);
    registerAlias("object", Object.class);

    registerAlias("date[]", Date[].class);
    registerAlias("decimal[]", BigDecimal[].class);
    registerAlias("bigdecimal[]", BigDecimal[].class);
    registerAlias("biginteger[]", BigInteger[].class);
    registerAlias("object[]", Object[].class);

    registerAlias("map", Map.class);
    registerAlias("hashmap", HashMap.class);
    registerAlias("list", List.class);
    registerAlias("arraylist", ArrayList.class);
    registerAlias("collection", Collection.class);
    registerAlias("iterator", Iterator.class);

    // 留意这个，之前resultMap中自己没有使用过的
    registerAlias("ResultSet", ResultSet.class);
  }

  /**
   * 解析别名
   * 通过别名获取具体的类型，如果获取不到则加载并初始化返回Class对象
   *
   * @param string
   * @param <T>
   * @return
   */
  @SuppressWarnings("unchecked")
  // throws class cast exception as well if types cannot be assigned
  // 如果无法分配类型，则也会引发类强制转换异常
  public <T> Class<T> resolveAlias(String string) {
    try {
      if (string == null) {
        return null;
      }
      // issue #748
      // 将别名转换为小写
      String key = string.toLowerCase(Locale.ENGLISH);
      Class<T> value;
      if (typeAliases.containsKey(key)) {
        // 通过别名获取类型
        value = (Class<T>) typeAliases.get(key);
      } else {
        // 不存在则加载并初始化
        value = (Class<T>) Resources.classForName(string);
      }
      return value;
    } catch (ClassNotFoundException e) {
      throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
    }
  }

  /**
   * 扫描包批量注册别名
   *
   * @param packageName
   */
  public void registerAliases(String packageName) {
    registerAliases(packageName, Object.class);
  }

  /**
   * 注册别名，忽略内部类、接口和抽象类
   *
   * @param packageName
   * @param superType
   */
  public void registerAliases(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();
    for (Class<?> type : typeSet) {
      // Ignore inner classes and interfaces (including package-info.java)
      // Skip also inner classes. See issue #6
      //
      if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
        registerAlias(type);
      }
    }
  }

  /**
   * 通过注解 @Alias 注册别名，value指定别名名称
   *
   * @param type
   */
  public void registerAlias(Class<?> type) {
    // 类的简单名字，不包含报名
    String alias = type.getSimpleName();
    Alias aliasAnnotation = type.getAnnotation(Alias.class);
    if (aliasAnnotation != null) {
      alias = aliasAnnotation.value();
    }
    registerAlias(alias, type);
  }

  public void registerAlias(String alias, Class<?> value) {
    if (alias == null) {
      throw new TypeException("The parameter alias cannot be null");
    }
    // issue #748
    String key = alias.toLowerCase(Locale.ENGLISH);
    // 对于相同的别名和java类型，如果注册多次则抛出异常
    if (typeAliases.containsKey(key) && typeAliases.get(key) != null && !typeAliases.get(key).equals(value)) {
      throw new TypeException("The alias '" + alias + "' is already mapped to the value '" + typeAliases.get(key).getName() + "'.");
    }
    typeAliases.put(key, value);
  }

  /**
   * @param alias 别名
   * @param value java类型权限定名
   */
  public void registerAlias(String alias, String value) {
    try {
      registerAlias(alias, Resources.classForName(value));
    } catch (ClassNotFoundException e) {
      throw new TypeException("Error registering type alias " + alias + " for " + value + ". Cause: " + e, e);
    }
  }

  /**
   * 获取类型别名
   *
   * @since 3.2.2
   */
  public Map<String, Class<?>> getTypeAliases() {
    return Collections.unmodifiableMap(typeAliases);
  }

}
