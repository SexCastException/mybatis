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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 所有的类型转换器都继承了TypeHandler 接口
 * <p>
 * 一般情况下，TypeHandler 用于完成单个参数以及单个列值的类型转换，如果存在多列值转换成一个Java对象的需求，
 * 应该优先考虑使用在映射文件中定义合适的映射规则(<resultMap>节点)完成映射。
 *
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /**
   * 通过${@link PreparedStatement}为SQL语句绑定参数时，会将数据由JdbcType类型转换成Java类型
   *
   * @param ps
   * @param i
   * @param parameter
   * @param jdbcType
   * @throws SQLException
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * 从${@link ResultSet}中获取数据时会调用此方法，会将数据由Java类型转换成JdbcType类型
   * <p>
   * 通过列名获取结果
   *
   * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  /**
   * 通过下标索引获取结果
   *
   * @param rs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   * 通过列下标的方式来获取存储过程输出结果中的数据
   *
   * @param cs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
