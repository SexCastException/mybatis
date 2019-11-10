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

import org.apache.ibatis.reflection.invoker.*;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * Reflector是MyBatis中反射模块的基础，每个Reflector 对象都对应一个类，在Reflector中缓存了反射操作需要使用的类的元信息
 *
 * <p>
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  // 需要反射的Class对象
  private final Class<?> type;
  // 可读属性名称集合，对应着getter方法的属性
  private final String[] readablePropertyNames;
  // 可写属性名称集合，对应着setter方法的属性
  private final String[] writablePropertyNames;
  // 保存属性相应setter方法，key是属性名称，value是 Invoker对象，它是对 setter方法对应的 Method对象的封装
  private final Map<String, Invoker> setMethods = new HashMap<>();
  // 保存属性相应getter方法，key是属性名称，value是 Invoker对象，它是对 getter方法对应的 Method对象的封装
  private final Map<String, Invoker> getMethods = new HashMap<>();
  // 保存属性相应的 setter方法的参数值类型， key是属性名称， value是setter 方法的参数类型
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  // 保存属性相应的 getter方法的参数值类型， key是属性名称， value是getter 方法的返回值类型
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  // 保存默认的构造方法
  private Constructor<?> defaultConstructor;
  // 保存所有属性名称的集合，不区分大小写
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    // 查找 clazz 的默认构造方法（无参构造方法）
    addDefaultConstructor(clazz);
    // 处理 clazz 中的 getter 方法，初始化 getMethods 集合和 getTypes 集合
    addGetMethods(clazz);
    // 处理 clazz 中的 setter 方法，初始化 setMethods 集合和 setTypes 集合
    addSetMethods(clazz);
    // 处理没有 getter 和 setter 方法的字段，并且该字段不是 final 和 static 的
    addFields(clazz);

    // 根据 getMethods和setMethods 集合，初始化 readablePropertyNames 和 writablePropertyNames 属性的名称集合
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);

    // 将readablePropertyNames 和 writablePropertyNames 名称集合转为大写并保存在 caseInsensitivePropertyMap 中
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 使用了lambda表达式，所以该版本的mybatis要求最低JDK环境为 1.8
   *
   * @param clazz
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获取所有的构造函数
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 遍历参数0的构造函数（无参构造函数）并初始化 defaultConstructor
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 负责解析类中定义的 getter 方法，
   *
   * @param clazz
   */
  private void addGetMethods(Class<?> clazz) {
    // key 为属性名称，value为相应的getter方法的Method集合，可能发生子类覆盖父类，所以同一属性可能有多个getter方法
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 1、获取指定类中以及父类和接口中定义的方法（不包含Object的方法）
    Method[] methods = getClassMethods(clazz);
    // 2、按照 JavaBean 规范查找 getter 法（过滤不是getter的方法并且参数列表不为空的方法），并保存到 conflictingGetters 集合中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 3、处理conflictingGetters中冲突的方法，并保存合适的getter方法到 getMethods 成员
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 处理冲突的getter方法
   *
   * 例如现有类A及其子类SubA，A类中定义了getNames() 方法，其返回值类型是List<String> ,而在其子类SubA中,覆写了其getNames() 方法
   * 且将返回值修改成ArrayList<String>类型，这种覆写在Java语言中是合法的。最终得到的两个方法签名分别是java.util.List#getNamnes
   * 和java.util.ArrayList#getNames，在Reflector.addUniqueMethods()方法中会被认为是两个不同的方法并添加到uniqueMethods集合中，
   * 这显然不是我们想要的结果。
   * @param conflictingGetters
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历集合
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        // 只有第一次遍历时才调用
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 当前最适合的方法是当前方法返回值的子类，即但前最适合的是winnerType，什么都不做
          // 例如：Object getA() 和 String getA() ,最适合的是方法是 String getA()。
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          // getter 类型是后代
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 当前最适合的是 candidateType，更新临时变量
          winner = candidate;
        } else {
          // 产生歧义，记录歧义标识，以便在addGetMethod方法中通过AmbiguousMethodInvoker报保存异常错误信息
          // 旧版本此处抛出ReflectorException异常
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 如果isAmbiguous为true，产生歧义，保存Invoker的同时保存错误信息，直到调用invoke方法时抛出
    MethodInvoker invoker = isAmbiguous
      ? new AmbiguousMethodInvoker(method, MessageFormat.format(
      "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
      name, method.getDeclaringClass().getName()))
      : new MethodInvoker(method);
    getMethods.put(name, invoker);
    // 获取方法返回值Type
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Class<?> clazz) {
    // key 为属性名称，value为相应的setter方法的Method集合，可能发生子类覆盖父类，所以同一属性可能有多个setter方法
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 1、获取指定类中以及父类和接口中定义的方法（不包含Object的方法）
    Method[] methods = getClassMethods(clazz);
    // 2、按照 JavaBean 规范查找 setter 法（过滤不是setter的方法并且参数列表不为1的方法），并保存到 conflictingSetters 集合中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 3、处理 conflictingSetters 中冲突的方法，并保存合适的setter方法到 getSethods 成员
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      // 通过属性名称判断是否该字段在getter方法中是否发生过冲突
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        // 如果当前字段在 getter 没有发生过冲突，并且该字段的 setter 方法的第一个形参类型式 getter 方法的返回值，则是该字段的最佳 setter 方法
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        // 否则该字段的 getter 方法发生冲突，并且 setter 方法没有发生冲突
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 挑选更好的 setter 方法
   *
   * @param setter1
   * @param setter2
   * @param property
   * @return
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 运行到此处表示 paramType1.equals(paramType2)，方法之间发生冲突
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
      MessageFormat.format(
        "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
        property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    // 获取指定类声明的字段
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // 获取字段的权限
        int modifiers = field.getModifiers();
        // 如果不是 final 和 static ，则加入 setMethods 成员
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      // 处理父类中定义的方法
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    // 获取有效属性的方法名称
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    // 获取有效属性的方法名称
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 不以$开头、且不是serialVersionUID字段和不是class的方法名称才算是有效的属性的方法名称
   *
   * @param name
   * @return
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 获取指定类中以及父类和接口中所有不重复的方法（不包含被子类覆盖的方法以及Object的方法）
   * <p>
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    // 用于保存指定类中定义的全部方法的唯一签名以及对应的 Method 对象
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    // 不包含Object的方法
    while (currentClass != null && currentClass != Object.class) {
      // 保存 currentClass 中声明的全部方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 保存 currentClass 实现的接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 回溯至父类，继续遍历
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 忽略桥接方法（泛型相关概念）
      if (!currentMethod.isBridge()) {
        // 获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 检测是否在子类 中已经添加过该方法，如果在子类中已经添加过，则示子类覆盖了该方法，无须再向 uniqueMethods 合中添加该方法了
        if (!uniqueMethods.containsKey(signature)) {
          // 保存签名和方法的对应关系
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获取方法签名，格式：返回值类型#方法名称:参数类型列表，此方法得到的方法签名是全局唯一的，可以作为该方法的唯一标识
   * 例如此方法的返回值是：java.lang.String#getSignature:java.lang.reflect.Method
   *
   * @param method
   * @return
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 获取指定方法返回类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    // 获取指定方法名称
    sb.append(method.getName());
    // 获取指定方法参数列表
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * 检查成员变量是否可以被访问
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        // （允许利用反射检查任意类的私有变量）
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
