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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * “orders[O].items[O].name”，类似这种由”.”和“[]”组成的表达式是由 PropertyTokenizer进行解析的。
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  //当前表达式的名称，比如orders
  private String name;
  // 当前表达式的索引名，比如orders[O]
  private final String indexedName;
  // 索引下标，并非一定是数字，索引不为空，则表示带有集合或数组的属性
  private String index;
  // 子表达式
  private final String children;

  /**
   * 例如传入 orders[0].items[0].name
   *
   * @param fullname
   */
  public PropertyTokenizer(String fullname) {
    // “.”作为当前表达式和子表达式的分隔符
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      // “.”前面为当前表达式，“.”后面为子表达式
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    // 解析当前表达式带有“[]”的内容
    indexedName = name;
    delim = name.indexOf('[');
    if (delim > -1) {
      // 获取“[”和“]”中间的索引，并非一定是数字
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  /**
   * 不提供移除功能，调用抛出异常
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
