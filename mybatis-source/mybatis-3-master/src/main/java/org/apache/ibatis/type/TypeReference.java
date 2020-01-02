/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * References a generic type.
 * <p>
 * TypeReference抽象类则定义了一个类型引用，用于引用一个泛型类型
 * <p>
 * 这个类型引用的作用适用于获取原生类型，Java中的原生类型又称为基本类型，即byte、short、int、long、float、double、boolean、char八大基本数据类型
 *
 * @param <T> the referenced type
 * @author Simone Tripodi
 * @since 3.1.0
 */
public abstract class TypeReference<T> {

  /**
   * 引用的原生类型
   */
  private final Type rawType;

  protected TypeReference() {
    rawType = getSuperclassTypeParameter(getClass());
  }

  /**
   * 获取泛型父类的类型参数
   *
   * @param clazz
   * @return
   */
  Type getSuperclassTypeParameter(Class<?> clazz) {
    // 获取超类，可能是泛型
    Type genericSuperclass = clazz.getGenericSuperclass();
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      // 如果TypeReference泛型嵌套TypeReference，则会进入此方法
      if (TypeReference.class != genericSuperclass) {
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }

      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }

    // 获取类型参数，即T的实际类型
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    // 如果T是泛型，则进入此方法
    if (rawType instanceof ParameterizedType) {
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

  public static void main(String[] args) {
    IntegerTypeHandler integerTypeHandler = new IntegerTypeHandler();
    if (integerTypeHandler.getClass().getGenericSuperclass() instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) integerTypeHandler.getClass().getGenericSuperclass();
      Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
      System.out.println(actualTypeArguments);
      System.out.println(actualTypeArguments[0]);
    }
  }

}
