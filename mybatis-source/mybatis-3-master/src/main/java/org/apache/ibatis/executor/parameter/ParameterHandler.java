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
package org.apache.ibatis.executor.parameter;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 主要功能是为SQL语句绑定实参，即使用传入的实参替换掉SQL语句中的“?”占位符，而每个“?”占位符都对应了
 * {@link BoundSql#parameterMappings}集合中的一个元素，在该 {@link ParameterMapping}对象中记录了对应的参数名称以及该参数的相关属性。
 * A parameter handler sets the parameters of the {@code PreparedStatement}.
 *
 * @author Clinton Begin
 */
public interface ParameterHandler {

  Object getParameterObject();

  /**
   * 为SQL语句绑定实参
   *
   * @param ps
   * @throws SQLException
   */
  void setParameters(PreparedStatement ps)
    throws SQLException;

}
