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
 * 属性分词器，例如：orders[0].items[0].name，有[]和.组成的表达式
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  /**
   * 表达式的名称
   */
  private String name;
  /**
   * 表达式的索引
   */
  private final String indexedName;
  /**
   * 索引下标
   */
  private String index;
  /**
   * 子表达式
   */
  private final String children;

  /**
   * 例如传入 orders[0].items[0].name
   * @param fullname
   */
  public PropertyTokenizer(String fullname) {
    // 查找“.”的位置，使用“.”分割表达式
    int delim = fullname.indexOf('.');  // 9
    if (delim > -1) {
      name = fullname.substring(0, delim);  // name：orders[0]
      children = fullname.substring(delim + 1); // children：items[0].name
    } else {  //
      name = fullname;
      children = null;
    }
    indexedName = name; // indexedName：orders[0]
    delim = name.indexOf('[');  // 6
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1); // index：0
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

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
