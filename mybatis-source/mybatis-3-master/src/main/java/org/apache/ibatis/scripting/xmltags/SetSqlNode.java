/**
 * Copyright 2009-2018 the original author or authors.
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

import org.apache.ibatis.session.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * SetSqlNode继承了 {@link TrimSqlNode}，其中字段prefix 字段为“SET”, suffixesToOverride 集合中的项只有“,”,
 * suffix字段和prefixesToOverride集合为null。也就是说，<set>节点解析后的SQL语句片段如果以“,”，结尾，
 * 则将结尾处的“,”删除掉，之后再将“SET"关键字添加到SQL片段的开始位置，从而得到该&lt;set>节点最终生成的SQL片段。
 *
 * @author Clinton Begin
 */
public class SetSqlNode extends TrimSqlNode {

  private static final List<String> COMMA = Collections.singletonList(",");

  public SetSqlNode(Configuration configuration, SqlNode contents) {
    // <set>节点默认前缀是 SET
    super(configuration, contents, "SET", COMMA, null, COMMA);
  }

}
