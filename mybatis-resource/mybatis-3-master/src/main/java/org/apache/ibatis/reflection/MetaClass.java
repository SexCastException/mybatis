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
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * MetaClass 是 MyBatis 类级别的元信息的封装和处理
 * 通过Reflector 和PropertyTokenizer 组合封装使用，实现了对复杂的属性表达式的解析，并实现了获取指定属性描述信息的功能。
 *
 * @author Clinton Begin
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  /**
   * 构造器私有化，通过forClass()静态方法实例化MetaClass对象
   *
   * @param type             根据type创建对应的Reflector对象
   * @param reflectorFactory
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 构造函数私有化，静态方法创建MetaClass对象
   *
   * @param type
   * @param reflectorFactory
   * @return
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 解析类属性的类型，主要是复杂属性
   *
   * @param name
   * @return
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 是否使用驼峰命名法，true，则忽略“_”
   *
   * @param name
   * @param useCamelCaseMapping
   * @return
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 返回setter方法的Class对象，如果有嵌套则返回最有一级字段的属性，比如user.order.name，最终返回name的setter方法的形参类型
   * 从Reflector的{@link Reflector#setTypes}集合中获取
   *
   * @param name
   * @return
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {  // 递归出口
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 返回getter方法返回值Class对象，如果有嵌套则返回最有一级字段的属性，比如user.order.name，最终返回name的getter方法的返回值类型
   * 与getSetterType()方法不同的是，getSetterType从setTypes集合中获取，getGetterType()方法从Reflector封装字段的{@link MethodInvoker}或{@link GetFieldInvoker}中获取
   *
   * @param name
   * @return
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    // 递归出口
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 获取getter方法的返回值类型，从Reflector封装字段的{@link MethodInvoker}或{@link GetFieldInvoker}中获取
   *
   * @param prop
   * @return
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    // 该表达式中是否使用"[]"指定了下标，且是Collection子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        // 针对ParameterizedType进行处理，即针对泛型集合类型进行处理
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        // 如果泛型指定类型参数，则返回参数的类型，如：List<String> 返回的是 String
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 根据Reflector.getMethods集合中记录的Invoker实现类的类型，决定解析getter方法返回值类型还是解析字段类型
      if (invoker instanceof MethodInvoker) {   // 如果是封装方法的 Invoker
        // 通过反射获取MethodInvoker的method调用getter方法
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {  // 如果是封装getter方法的 Invoker
        // MethodInvoker属性method由于没有提供getter方法，所以通过反射来获取，GetFieldInvoker 属性field同理
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  /**
   * 递归查询是否有setter方法，只要有一个属性的setter方法不存在，则返回false
   *
   * @param name
   * @return
   */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 递归查询是否有getter方法，只要有一个属性没有getter方法，则返回false
   *
   * @param name
   * @return
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 解析属性表达式,比如：orders[O].items[O].name
   *
   * @param name    解析属性表达式
   * @param builder
   * @return
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // caseInsensitivePropertyMap中获取属性名称，忽略大小写
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归解析 PropertyTokenizer.children 字段，并将解析结果添加到 builder 中保存
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {  // 递归出口
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  /**
   * 是否有默认的构造函数
   *
   * @return
   */
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
