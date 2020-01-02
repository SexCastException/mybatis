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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * ObjectWrapper 接口是对对象的包装，抽象了对象的属性信息，它定义了一系列查询对象属性信息的方法，以及更新属性的方法。
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 如果ObjectWrapper中封装的是普通的Bean对象，则调用相应属性的相应getter方法，
   * 如果封装的是集合类，则获取指定key或下标对应的value值
   *
   * @param prop
   * @return
   */
  Object get(PropertyTokenizer prop);


  /**
   * 如果ObjectWrapper中封装的是普通的Bean对象，则调用相应属性的相应setter方法，
   * 如果封装的是集合类，则设置指定key或下标对应的value值
   *
   * @param prop
   * @param value
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 查找属性表达式指定的属性，第二个参数表示是否忽略属性表达式中的下画线
   *
   * @param name
   * @param useCamelCaseMapping 是否忽略属性表达式中的下画线
   * @return
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 查找可写属性的名称集合
   *
   * @return
   */
  String[] getGetterNames();

  /**
   * 查找可读属性的名称集合
   *
   * @return
   */
  String[] getSetterNames();

  /**
   * 解析属性表达式指定属性的 setter 方法的参数类型
   *
   * @param name
   * @return
   */
  Class<?> getSetterType(String name);

  /**
   * 解析属性表达式指定属性的 getter 方法的返回值类型
   *
   * @param name
   * @return
   */
  Class<?> getGetterType(String name);

  /**
   * 判断属性表达式指定属性是否有 setter 方法
   *
   * @param name
   * @return
   */
  boolean hasSetter(String name);

  /**
   * 判断属性表达式指定属性是否有 getter 方法
   *
   * @param name
   * @return
   */
  boolean hasGetter(String name);

  /**
   * 为属性表达式指定的属性创建相应的 MetaObject 对象
   *
   * @param name
   * @param prop
   * @param objectFactory
   * @return
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 封装的对象是否为 Collection 类型
   *
   * @return
   */
  boolean isCollection();

  /**
   * 调用Collection 对象的 add 方法
   *
   * @param element
   */
  void add(Object element);

  /**
   * 调用Collection 对象的 addAll 方法
   *
   * @param element
   * @param <E>
   */
  <E> void addAll(List<E> element);

}
