/**
 * Copyright 2009-2017 the original author or authors.
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

import java.util.List;

/**
 * 如果在编写动态SQL语句时需要类似Java中的switch语句的功能,可以考虑使用&lt;choose>、&lt;when>和&lt;otherwise>三个标签的组合。
 * <p>
 * MyBatis 会将&lt;choose>标签解析成 {@link ChooseSqlNode}，将&lt;when>标签解析成 {@link IfSqlNode},将&lt;otherwise>标签解析成 {@link MixedSqlNode}。
 *
 * @author Clinton Begin
 */
public class ChooseSqlNode implements SqlNode {
  /**
   * &lt;otherwise>节点对应的 {@link SqlNode}
   */
  private final SqlNode defaultSqlNode;
  /**
   * &lt;when>节点对应的 {@link IfSqlNode}
   */
  private final List<SqlNode> ifSqlNodes;

  public ChooseSqlNode(List<SqlNode> ifSqlNodes, SqlNode defaultSqlNode) {
    this.ifSqlNodes = ifSqlNodes;
    this.defaultSqlNode = defaultSqlNode;
  }

  /**
   * 首先遍历 {@link ChooseSqlNode#ifSqlNodes}集合并调用 {@link SqlNode#apply(DynamicContext)}方法，
   * 然后根据前面的处理结果决定是否调用 {@link ChooseSqlNode#defaultSqlNode}的{@link SqlNode#apply(DynamicContext)}方法。
   *
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 遍历调用 ifSqlNodes 节点，<choose>可能配置了多个<when>节点，有一个为true，即表示命中其中一个，结束方法
    for (SqlNode sqlNode : ifSqlNodes) {
      if (sqlNode.apply(context)) {
        return true;
      }
    }
    if (defaultSqlNode != null) {
      defaultSqlNode.apply(context);
      return true;
    }
    return false;
  }
}
