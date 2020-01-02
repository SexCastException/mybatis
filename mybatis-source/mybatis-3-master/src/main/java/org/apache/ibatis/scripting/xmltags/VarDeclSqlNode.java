/**
 * Copyright 2009-2015 the original author or authors.
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

/**
 * VarDeclSqlNode表示的是动态SQL语句中的<bind>节点,该节点可以从OGNL表达式中创建一个变量并将其记录到上下文中。
 * 在VarDeclSqlNode 中通过name字段记录<bind>节点的name属性值，expression 字段记录<bind>节点的value属性值。
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class VarDeclSqlNode implements SqlNode {

  private final String name;
  private final String expression;

  public VarDeclSqlNode(String var, String exp) {
    name = var;
    expression = exp;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 解析OGNL表达式的值
    final Object value = OgnlCache.getValue(expression, context.getBindings());
    // 绑定name和表达式的值
    context.bind(name, value);
    return true;
  }

}
