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

import org.apache.ibatis.io.Resources;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 未知类型处理器，通过列名或列索引获取相应的{@link TypeHandler}对象，获取不到则默认使用{@link ObjectTypeHandler}
 *
 * @author Clinton Begin
 */
public class UnknownTypeHandler extends BaseTypeHandler<Object> {

  private static final ObjectTypeHandler OBJECT_TYPE_HANDLER = new ObjectTypeHandler();

  private TypeHandlerRegistry typeHandlerRegistry;

  public UnknownTypeHandler(TypeHandlerRegistry typeHandlerRegistry) {
    this.typeHandlerRegistry = typeHandlerRegistry;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
    throws SQLException {
    // 根据 parameter 类对象解析获取对应的 TypeHandler 对象
    TypeHandler handler = resolveTypeHandler(parameter, jdbcType);
    // 调用解析的 TypeHandler 来进行JavaType和JdbcType类型转换
    handler.setParameter(ps, i, parameter, jdbcType);
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName)
    throws SQLException {
    TypeHandler<?> handler = resolveTypeHandler(rs, columnName);
    return handler.getResult(rs, columnName);
  }

  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex)
    throws SQLException {
    // 根据列索引解析 TypeHandler
    TypeHandler<?> handler = resolveTypeHandler(rs.getMetaData(), columnIndex);
    // 解析失败则默认使用 ObjectTypeHandler
    if (handler == null || handler instanceof UnknownTypeHandler) {
      handler = OBJECT_TYPE_HANDLER;
    }
    return handler.getResult(rs, columnIndex);
  }

  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex)
    throws SQLException {
    return cs.getObject(columnIndex);
  }

  /**
   * 根据 parameter 类对象解析获取对应的 {@link TypeHandler 对象
   *
   * @param parameter
   * @param jdbcType
   * @return
   */
  private TypeHandler<?> resolveTypeHandler(Object parameter, JdbcType jdbcType) {
    TypeHandler<?> handler;
    if (parameter == null) {
      handler = OBJECT_TYPE_HANDLER;
    } else {
      handler = typeHandlerRegistry.getTypeHandler(parameter.getClass(), jdbcType);
      // check if handler is null (issue #270)
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = OBJECT_TYPE_HANDLER;
      }
    }
    return handler;
  }

  private TypeHandler<?> resolveTypeHandler(ResultSet rs, String column) {
    try {
      // 保存数据库结果集列名和列索引映射关系（key为列名，value为列索引）
      Map<String, Integer> columnIndexLookup;
      columnIndexLookup = new HashMap<>();
      ResultSetMetaData rsmd = rs.getMetaData();
      // 获取结果集列个数
      int count = rsmd.getColumnCount();
      for (int i = 1; i <= count; i++) {
        String name = rsmd.getColumnName(i);
        columnIndexLookup.put(name, i);
      }
      // 根据需要映射的列名获取该列名的列索引
      Integer columnIndex = columnIndexLookup.get(column);
      TypeHandler<?> handler = null;
      // 获取该列对应的TypeHandler对象
      if (columnIndex != null) {
        handler = resolveTypeHandler(rsmd, columnIndex);
      }
      // 获取失败则默认使用 ObjectTypeHandler
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = OBJECT_TYPE_HANDLER;
      }
      return handler;
    } catch (SQLException e) {
      throw new TypeException("Error determining JDBC type for column " + column + ".  Cause: " + e, e);
    }
  }

  /**
   * 根据列索引解析{@link TypeHandler}
   * @param rsmd
   * @param columnIndex 数据查询结果集列索引
   * @return
   */
  private TypeHandler<?> resolveTypeHandler(ResultSetMetaData rsmd, Integer columnIndex) {
    TypeHandler<?> handler = null;
    JdbcType jdbcType = safeGetJdbcTypeForColumn(rsmd, columnIndex);
    Class<?> javaType = safeGetClassForColumn(rsmd, columnIndex);
    // 根据以上获取的jdbcType和javaType获取TypeHandler对象
    if (javaType != null && jdbcType != null) {
      handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
    } else if (javaType != null) {
      handler = typeHandlerRegistry.getTypeHandler(javaType);
    } else if (jdbcType != null) {
      handler = typeHandlerRegistry.getTypeHandler(jdbcType);
    }
    return handler;
  }

  /**
   * 根据列索引（本质通过列的数据库类型的code）获取该数据列的{@link JdbcType}
   *
   * @param rsmd
   * @param columnIndex
   * @return
   */
  private JdbcType safeGetJdbcTypeForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      return JdbcType.forCode(rsmd.getColumnType(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }

  private Class<?> safeGetClassForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      return Resources.classForName(rsmd.getColumnClassName(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }
}
