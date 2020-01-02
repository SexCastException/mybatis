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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * Invoker的四个实现类：{@link MethodInvoker}、{@link GetFieldInvoker}、{@link SetFieldInvoker}和{@link AmbiguousMethodInvoker}
 * <p>
 * 当类的字段有对应的Getter方法和Setter方法时，将该字段封装成{@link MethodInvoker}，当类字段没有getter方法时，将字段封装成{@link GetFieldInvoker}
 * 当类的字段没有setter方法时，将类的字段封装成{@link GetFieldInvoker}，当类的字段发生冲突时，将该字段封装成{@link AmbiguousMethodInvoker}，调用时抛出异常
 *
 * @author Clinton Begin
 */
public interface Invoker {
  /**
   * 调用获取指定字段的值或执行指定的方法
   *
   * @param target
   * @param args
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  /**
   * 返回属性相应的类型
   *
   * @return
   */
  Class<?> getType();
}
