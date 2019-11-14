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
package org.apache.ibatis.reflection;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * 提供了一系列静态方法来解析指定类中的字段、方法返回值或方法参数的类型。
 *
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * 解析字段
   *
   * @param field
   * @param srcType
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   * they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    // 获取字段声明的类型
    Type fieldType = field.getGenericType();
    // 获取字段所在的 Class 对象
    Class<?> declaringClass = field.getDeclaringClass();
    // 解析类型
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * 解析方法返回值类型
   *
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   * they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    // 获取方法返回值类型
    Type returnType = method.getGenericReturnType();
    // 此方法声明的类
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * 解析方法参数类型
   *
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   * they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    // 获取参数类型
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    // 保存解析后的结果并返回
    Type[] result = new Type[paramTypes.length];
    // 存在过个参数，遍历解析
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /**
   * Class：常见的原始类和接口，如String和Serializable
   * ParameterizedType：表示的是参数化类型，例如：List<String> 和 Map<String,Integer>
   * TypeVariable：表示的是类型变量（即：泛型参数），它用来反映在 JVM 编译该泛型前的信息，它在编译时需被转换为一个具体的类型后才能正常使用。
   * GenericArrayType：表示的是数组类型且组成元素是 ParameterizedType 或 TypeVariable，例如：List<String>[] 或 T[]
   * WildcardType：表示的是通配符泛型，例如：? extends Number 和 ? super Integer
   *
   * @param type           根据type（字段、方法返回值和方法参数类型）选择合适的方法解析
   * @param srcType        srcType表示查找该字段、返回值或方法参数类型的起始位置
   * @param declaringClass 表示type定义所在的类
   * @return
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    // 根据 type（可能是字段、方法返回值和方法参数的类型） 的类型选择合适的解析方法
    if (type instanceof TypeVariable) {
      // 解析 TypeVariable 类型
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      // 解析 ParameterizedType 类型
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      // 解析 GenericArrayType 类型
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {  // Class 类型
      return type;
    }
    // 字段、返回值或参数不可以直接定义成 WildcardType 类型，但可以嵌套在别的类型中，所以没有 wildcardType 分支
  }

  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    // 获取数组元素类型
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    // 根据数组元素类型选择合适的方法进行解析
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    // 根据解析后的数组项类型构造返回类型
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * 解析泛型参数类型
   *
   * @param parameterizedType 待解析的泛型类型
   * @param srcType           解析操作的起始类型
   * @param declaringClass    定义该字段或方法的类的 Class 对象
   * @return
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    // 例如：Map<K,V>
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 实际的泛型参数类型，即：K和V的实际参数
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    // 用于保存解析泛型参数类型后的结果
    Type[] args = new Type[typeArgs.length];
    // 遍历解析 K 和 V
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {  // 解析类型变量
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);

      } else if (typeArgs[i] instanceof ParameterizedType) {  // 如果嵌套了 ParameterizedType（泛型嵌套泛型），则递归调用处理
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);

      } else if (typeArgs[i] instanceof WildcardType) { // 解析通配符泛型，比如：? extend Number
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);

      } else {
        args[i] = typeArgs[i];
      }
    }
    // 将解析后的结果封装成 ParameterizedType 并返回
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  /**
   * 解析通配符泛型，比如：? extend Number
   *
   * @param wildcardType
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    // 获取泛型变量的上界
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    // 获取泛型变量的下界
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 实际解析的是typeVar声明的类，比如Map<String,Object> 实际解析的是Map
   *
   * @param typeVar        待解析的类型参数
   * @param srcType        声明typeVar的类
   * @param declaringClass typeVar所在声明的类
   * @return
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    // 因为 SubClassA 继承了 ClassA 且 map 字段定义在 ClassA中，故这里的 srcType 与 declaringClass 并不相等。
    // 如果不是从某个确定了具体类型的泛型参数的子类开始解析，则解析结果是该泛型变量的上界，默认是Object
    // 如：SubA<Long,String> 如果从子类Sub开始解析，结果：ClassA<Long,String>
    // 如：直接从ClassA<K,V>解析，则结果为K和V的上界，默认为Object
    if (clazz == declaringClass) {
      // 获取上界，没有写，则为Object
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    // 获取声明的父亲类型
    Type superclass = clazz.getGenericSuperclass();
    // 扫描父类进行后续解析，递归入口，即resolveTypeVar调用scanSuperTypes，scanSuperTypes调用resolveTypeVar.......
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      // 扫描接口进行后续解析
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    // 若在整个集成结构中没有解析成功，则返回 Object.class
    return Object.class;
  }

  /**
   * 和{@link TypeParameterResolver#resolveTypeVar}递归整个继承结构井完成类型变量的解析
   *
   * @param typeVar        待解析的类型参数
   * @param srcType        从哪个子类开始解析
   * @param declaringClass typeVar所在声明的类
   * @param clazz
   * @param superclass
   * @return
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    if (superclass instanceof ParameterizedType) {  // superclass是泛型类型
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      // 获取 superclass 的原始类型
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      // 获取 superclass 的泛型类型参数
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      if (srcType instanceof ParameterizedType) {
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      if (declaringClass == parentAsClass) {
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar == parentTypeVars[i]) {
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }

  /**
   * 将父类的泛型变量如K，V转换成子类的具体类型，如Long，String
   * SubA<Long,String> extends ClassA<K,V>  结果：SubA<Long,String> extends ClassA<Long,String>
   * SubA<Long,String> extends ClassA<V,K>  结果：SubA<Long,String> extends ClassA<String,Long>
   * SubA<Long,String> extends ClassA<List<K>,V>  结果：SubA<Long,String> extends ClassA<List<K>,String>
   * SubA<Long,String> extends ClassA<List<K>,List<V>>  结果：SubA<Long,String> extends ClassA<List<K>,List<V>>
   *
   * @param srcType
   * @param srcClass
   * @param parentType
   * @return
   */
  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
      if (parentTypeArgs[i] instanceof TypeVariable) {
        for (int j = 0; j < srcTypeVars.length; j++) {
          if (srcTypeVars[j] == parentTypeArgs[i]) {
            noChange = false;
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } else {
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>) parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
