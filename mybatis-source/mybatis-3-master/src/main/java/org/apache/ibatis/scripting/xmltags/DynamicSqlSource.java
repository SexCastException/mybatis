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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 执行时机：实际执行SQL语句，比如判断&lt;if>节点的test属性值，只有真正执行的时候根据实参来判断
 * <p>
 * 处理动态SQL语句，也是常用的 {@link SqlSource}之一
 *
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  /**
   * 待解析的 {@link SqlNode}树的根节点
   */
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * @param parameterObject 用户传入的实参
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    /*
      调用整个树形结构中全部SqlNode.apply()方法。每个SqlNode的apply()方法都将解析得到的SQL语句片段追加到context中，
      最终通过context.getSql()得到完整的SQL语句
     */
    rootSqlNode.apply(context);
    // 创建SqlSourceBuilder,解析参数属性，并将SQL语句中的“#{}”占位符替换成“?”占位符
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 如果没有指定parameterType属性值，则默认为Object类型
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());

    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 将DynamicContext.bindings 中的每一项参数信息复制到其metaParameters中保存
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
