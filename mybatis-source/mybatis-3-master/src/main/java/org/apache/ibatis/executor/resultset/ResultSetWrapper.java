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

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * {@link ResultSet}包装类
 * <p>
 * 在ResultSetWrapper中记录了{@link ResultSet} 中的一些元数据， 并且提供了一系列操作{@link ResultSet}的辅助方法。
 *
 * @author Iwao AVE!
 */
public class ResultSetWrapper {

  /**
   * 被包装的 {@link ResultSet}对象
   */
  private final ResultSet resultSet;
  private final TypeHandlerRegistry typeHandlerRegistry;
  /**
   * {@link ResultSet}中每列的名称
   */
  private final List<String> columnNames = new ArrayList<>();
  /**
   * {@link ResultSet}中每列对应的Java类型
   */
  private final List<String> classNames = new ArrayList<>();
  /**
   * {@link ResultSet}中每列对应的 {@link JdbcType}类型
   */
  private final List<JdbcType> jdbcTypes = new ArrayList<>();
  /**
   * 每列对应的 {@link TypeHandler}对象，key是列名，value是 {@link TypeHandler}集合
   */
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<>();
  /**
   * 被映射的列名，key是 {@link ResultMap}对象的id+列名前缀，value是该 {@link ResultMap}对象映射的列名集合
   */
  private final Map<String, List<String>> mappedColumnNamesMap = new HashMap<>();
  /**
   * 未映射的列名，key是 {@link ResultMap}对象的id+列名前缀，value是该 {@link ResultMap}对象映射的列名集合
   */
  private final Map<String, List<String>> unMappedColumnNamesMap = new HashMap<>();

  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.resultSet = rs;
    // 获取ResultSet的元信息
    final ResultSetMetaData metaData = rs.getMetaData();
    // ResultSet的列数
    final int columnCount = metaData.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      // 获取列名或通过“AS”指定的别名
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));
      // 该列的jdbcType类型
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));
      // 该列对应的Java类型
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public List<JdbcType> getJdbcTypes() {
    return jdbcTypes;
  }

  /**
   * 通过指定的列名返回对应的 {@link JdbcType}对象
   *
   * @param columnName
   * @return
   */
  public JdbcType getJdbcType(String columnName) {
    for (int i = 0; i < columnNames.size(); i++) {
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   *
   * @param propertyType
   * @param columnName
   * @return
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    // 保存解析的结果并返回
    TypeHandler<?> handler = null;
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    if (columnHandlers == null) {
      columnHandlers = new HashMap<>();
      typeHandlerMap.put(columnName, columnHandlers);
    } else {
      handler = columnHandlers.get(propertyType);
    }
    if (handler == null) {
      JdbcType jdbcType = getJdbcType(columnName);
      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      if (handler == null || handler instanceof UnknownTypeHandler) {
        final int index = columnNames.indexOf(columnName);
        final Class<?> javaType = resolveClass(classNames.get(index));
        if (javaType != null && jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
        } else if (javaType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType);
        } else if (jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = new ObjectTypeHandler();
      }
      columnHandlers.put(propertyType, handler);
    }
    return handler;
  }

  /**
   * 返回指定类名的 {@link Class}对象
   *
   * @param className
   * @return
   */
  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        // 使用类加载器加载指定类
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  /**
   * 把已经成功映射的列名加入 {@link ResultSetWrapper#mappedColumnNamesMap}集合
   * 把未成功映射的列名加入 {@link ResultSetWrapper#unMappedColumnNamesMap}集合
   *
   * @param resultMap
   * @param columnPrefix
   * @throws SQLException
   */
  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    // 保存成功映射的列名
    List<String> mappedColumnNames = new ArrayList<>();
    // 保存未成功映射的列名
    List<String> unmappedColumnNames = new ArrayList<>();
    // 大写的列名前缀
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);
    for (String columnName : columnNames) {
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      if (mappedColumns.contains(upperColumnName)) {
        mappedColumnNames.add(upperColumnName);
      } else {
        unmappedColumnNames.add(columnName);
      }
    }
    // 更新覆盖
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  /**
   * 返回指定 {@link ResultMap}对象中成功映射的列名集合，同时会将该列名集合以及未映射的列名集合记录到 {@link ResultSetWrapper#mappedColumnNamesMap}
   * 和 {@link ResultSetWrapper#unMappedColumnNamesMap}中缓存。
   *
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (mappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

  /**
   * 返回指定 {@link ResultMap}对象中未成功映射的列名集合，同时会将该列名集合以及未映射的列名集合记录到 {@link ResultSetWrapper#mappedColumnNamesMap}
   * 和 {@link ResultSetWrapper#unMappedColumnNamesMap}中缓存。
   *
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (unMappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return unMappedColumnNames;
  }

  /**
   * 返回由 {@link ResultMap}的id和列名前缀构成
   *
   * @param resultMap
   * @param columnPrefix
   * @return
   */
  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  /**
   * 前置前缀 格式：前缀+列名
   *
   * @param columnNames
   * @param prefix
   * @return
   */
  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    final Set<String> prefixed = new HashSet<>();
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }

}
