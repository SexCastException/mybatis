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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 * <p>
 * MyBatis使用SqlSource接口表示映射文件或注解中定义的SQL语句，但它表示的SQL语句是不能直接被数据库执行的，
 * 因为其中可能含有动态SQL语句相关的节点或是占位符等需要解析的元素。
 *
 * @author Clinton Begin
 */
public interface SqlSource {

  /**
   * 根据映射文件或注解描述的SQL语句，以及传入的参数，返回可执行的SQL
   *
   * @param parameterObject
   * @return
   */
  BoundSql getBoundSql(Object parameterObject);

}
