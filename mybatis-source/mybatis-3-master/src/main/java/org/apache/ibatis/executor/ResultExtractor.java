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
package org.apache.ibatis.executor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;

/**
 * 负责将延迟加载的对象转换为目标类型的对象
 *
 * @author Andrew Gustafson
 */
public class ResultExtractor {
  private final Configuration configuration;
  private final ObjectFactory objectFactory;

  public ResultExtractor(Configuration configuration, ObjectFactory objectFactory) {
    this.configuration = configuration;
    this.objectFactory = objectFactory;
  }

  /**
   * 1、如果目标对象类型为 {@link List}，则无须转换，直接返回。<br>
   * 2、如果目标对象类型是 {@link Collection}子类，将 list 中每一项的值拷贝到新创建的目标类型的集合对象。<br>
   * 3、对于数组类型，如果目标类型是基本类型数组，将则list中每一项拷贝到新数组，否则，将list转化为数组返回<br>
   * 4、如果目标对象是普通Java对象且延迟加载得到的List大小为1,则认为将其中唯一的项作为转换后的对象返回。<br>
   *
   * @param list
   * @param targetType
   * @return
   */
  public Object extractObjectFromList(List<Object> list, Class<?> targetType) {
    // 保存转换的结果对象
    Object value = null;
    if (targetType != null && targetType.isAssignableFrom(list.getClass())) { // 1、
      value = list;
    } else if (targetType != null && objectFactory.isCollection(targetType)) {  // 2、
      // 创建targetType类型的对象
      value = objectFactory.create(targetType);
      // 创建targetType类型对象的元对象
      MetaObject metaObject = configuration.newMetaObject(value);
      // 将list中的数据赋值到targetType类型对象
      metaObject.addAll(list);
    } else if (targetType != null && targetType.isArray()) {  // 3、
      // 获取数据组件的类型
      Class<?> arrayComponentType = targetType.getComponentType();
      // 创建数组组件类型的数组对象
      Object array = Array.newInstance(arrayComponentType, list.size());
      // 判断数据组件类型是否为基本类型
      if (arrayComponentType.isPrimitive()) {
        // 将list中每项的值拷贝到新数组中
        for (int i = 0; i < list.size(); i++) {
          Array.set(array, i, list.get(i));
        }
        value = array;
      } else {
        value = list.toArray((Object[]) array);
      }
    } else {  // 4、
      // 校验当目标类型是普通java类型时，list是否有多项
      if (list != null && list.size() > 1) {
        throw new ExecutorException("Statement returned more than one row, where no more than one was expected.");
      } else if (list != null && list.size() == 1) {
        value = list.get(0);
      }
    }
    return value;
  }
}
