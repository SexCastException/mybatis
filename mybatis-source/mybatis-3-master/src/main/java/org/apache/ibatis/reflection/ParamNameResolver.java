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

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 参数名称解析器，用于处理Mapper接口中定义的方法的参数列表。
 */
public class ParamNameResolver {

  private static final String GENERIC_NAME_PREFIX = "param";

  /**
   * 记录了参数在参数列表中的位置索引与参数名称之间的对应关系,其中key表示参数在参数列表中的索引位置，
   * value表示参数名称，参数名称可以通过{@link Param}注解指定，如果没有指定{@link Param}注解，则使用参数索引作为其名称。
   * 如果参数列表中包含{@link RowBounds}类型或{@link ResultHandler}类型的参数,则这两种类型的参数并不会被记录到name集合中，
   * 这就会导致参数的索引与名称不一致，例如，method(int a, RowBounds rb, int b)方法对应的names集合为{{0, "0"}, {2, "1"}}，
   * 而此时使用{@link Param}问题就迎刃而解
   *
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  /**
   * 对应方法中的参数列表是否使用了 @Param 注解
   */
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    // 获取参数列表中每个参数的类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取参数列表中的注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    // key为参数所在形参列表中的索引
    final SortedMap<Integer, String> map = new TreeMap<>();
    // 参数列表中注解的一维个数，遍历时使用
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    // paramIndex每个参数在参数列表中对应的索引
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 如果参数是RowBounds类型或ResultHandler类型，则跳过对该参数的分析
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          // @Param注解出现过一次，就将hasParamAnnotation初始化为true
          hasParamAnnotation = true;
          // 使用@Param 注解指定的value作为参数名称
          name = ((Param) annotation).value();
          break;
        }
      }
      // 没有指定@Param注解
      if (name == null) {
        // @Param was not specified.
        if (config.isUseActualParamName()) {  // 根据全局配置文件判断是否配置了是否使用真实参数变量名称作为name
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          /*
             表明没有指定Param注解以及配置文件useActualParamName为false，则使用参数索引作为name值，
             Mapper.xml配置文件使用方式为#{"param"+name}，比如#{param0}，而"param"在获取的时候才拼接上去，
             此时只是记录需要使用时的下标
           */
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 是否为{@link RowBounds}或{@link ResultHandler}
   *
   * @param clazz
   * @return
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * 构造函数中解析的names集合主要在ParamNameResolver.getNamedParams(方法中使用，
   * 该方法接收的参数是用户传入的实参列表，并将实参与其对应名称进行关联
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args Mapper接口方法的实参列表
   * @return
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {  // 无参数，返回null
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      /*
        未使用@Param 且只有一个参数，则直接返回实参，
        如：只有一个形参列表时（String userName），此时在Mapper配置文件，不管我们是使用#{userName}还是#{aaa}或#{xxxx}
        都能拿到结果，但一般建议见名知意，推荐使用 #{userName}
       */
      return args[names.firstKey()];
    } else {  // 处理使用@Param注解指定了参数名称或有多个参数的情况
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        /*
            参数名和实参第一次映射：
            将参数名与实参对应关系记录到param中，entry.getKey()记录了形参的在方法签名的索引，而是args是实参数组，
            args[entry.getKey()获取到了形参对应下标的实参参数
         */
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 为参数创建"param+索引"格式的默认参数名称，例如: param1, param2 等，并添加到param集合中，从1开始
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // ensure not to overwrite parameter named with @Param
        // 如果@Param注解配置的形如“param1”就会出现冲突，确保不覆盖@Param注解配置的值与genericParamName相同的名字
        if (!names.containsValue(genericParamName)) {
          /*
            参数名和实参第二次映射：
           */
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
