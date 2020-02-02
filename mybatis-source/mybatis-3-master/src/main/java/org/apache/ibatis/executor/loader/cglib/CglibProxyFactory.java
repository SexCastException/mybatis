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
package org.apache.ibatis.executor.loader.cglib;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.ibatis.executor.loader.*;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用cglib创建代理对象
 *
 * @author Clinton Begin
 */
public class CglibProxyFactory implements ProxyFactory {

  private static final String FINALIZE_METHOD = "finalize";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";

  public CglibProxyFactory() {
    try {
      Resources.classForName("net.sf.cglib.proxy.Enhancer");
    } catch (Throwable e) {
      throw new IllegalStateException("Cannot enable lazy loading because CGLIB is not available. Add CGLIB to your classpath.", e);
    }
  }

  @Override
  public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
  }

  public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  /**
   * @param type                需要创建代理的目标类
   * @param callback
   * @param constructorArgTypes 创建代理对象时，使用的构造器形参列表类型
   * @param constructorArgs     创建代理对象时，使用的构造器实参列表值
   * @return
   */
  static Object crateProxy(Class<?> type, Callback callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    Enhancer enhancer = new Enhancer();
    // 设置 Callback对象
    enhancer.setCallback(callback);
    // 指定生成代理类的父类
    enhancer.setSuperclass(type);
    try {
      type.getDeclaredMethod(WRITE_REPLACE_METHOD);
      // ObjectOutputStream will call writeReplace of objects returned by writeReplace
      if (LogHolder.log.isDebugEnabled()) {
        LogHolder.log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
      }
    } catch (NoSuchMethodException e) {
      enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
    } catch (SecurityException e) {
      // nothing to do here
    }
    Object enhanced;
    // 如果参数列表为空，则通过默认构造函数创建代理对象
    if (constructorArgTypes.isEmpty()) {
      enhanced = enhancer.create();
    } else {  // 否则通过有参构造函数创建代理对象
      Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
      Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
      enhanced = enhancer.create(typesArray, valuesArray);
    }
    return enhanced;
  }

  /**
   * 实现了 {@link MethodInterceptor}接口，可以对指定的方法进行代理
   */
  private static class EnhancedResultObjectProxyImpl implements MethodInterceptor {

    /**
     * 需要创建代理的目标类
     */
    private final Class<?> type;
    /**
     * 记录了延迟加载的属性名称与对应 {@link ResultLoader}对象之间的关系
     */
    private final ResultLoaderMap lazyLoader;
    /**
     * 在mybatis-config.xml文件中，aggressiveLazyLoading 配置项的值，表示是否积极延迟加载
     */
    private final boolean aggressive;
    /**
     * 触发延迟加载的方法名列表，如果调用了该列表中的方法，则对全部的延迟加载属性进行加载操作
     */
    private final Set<String> lazyLoadTriggerMethods;
    private final ObjectFactory objectFactory;
    /**
     * 创建代理对象时，使用的构造器形参列表类型
     */
    private final List<Class<?>> constructorArgTypes;
    /**
     * 创建代理对象时，使用的构造器实参列表值
     */
    private final List<Object> constructorArgs;

    /**
     * 私有化构造器，通过 {@link EnhancedResultObjectProxyImpl#createProxy}方法实例化对象
     *
     * @param type
     * @param lazyLoader
     * @param configuration
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     */
    private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      this.type = type;
      this.lazyLoader = lazyLoader;
      this.aggressive = configuration.isAggressiveLazyLoading();
      this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
      this.objectFactory = objectFactory;
      this.constructorArgTypes = constructorArgTypes;
      this.constructorArgs = constructorArgs;
    }

    /**
     * 创建代理对象，并将目标对象的属性值赋值给代理对象
     *
     * @param target              需要创建代理的目标类
     * @param lazyLoader
     * @param configuration
     * @param objectFactory
     * @param constructorArgTypes 创建代理对象时，使用的构造器形参列表类型
     * @param constructorArgs     创建代理对象时，使用的构造器实参列表值
     * @return
     */
    public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
      // 创建代理对象
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      // 将目标被代理对象属性值拷贝到新生成的代理对象
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    /**
     * 当前指定的方法，决定是否触发对延迟加载的属性进行加载
     *
     * @param enhanced    代理对象
     * @param method
     * @param args
     * @param methodProxy
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
      final String methodName = method.getName();
      try {
        synchronized (lazyLoader) {
          if (WRITE_REPLACE_METHOD.equals(methodName)) {
            Object original;
            if (constructorArgTypes.isEmpty()) {  // 参数列表为空，通过默认构造函数创建原始对象
              original = objectFactory.create(type);
            } else {  // 否则通过有参构造函数创建原始对象
              original = objectFactory.create(type, constructorArgTypes, constructorArgs);
            }
            // 拷贝属性值
            PropertyCopier.copyBeanProperties(type, enhanced, original);
            if (lazyLoader.size() > 0) {
              return new CglibSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
            } else {
              return original;
            }
          } else {
            // 检测是否存在延迟加载的属性，以及调用方法名是否为“finalize”
            if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
              /*
                如果aggressiveLazyLoading配置项为true，或是调用方法的名称存在于lazyLoadTriggerMethods列表中，
                则将全部的属性都加载完成
               */
              if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                lazyLoader.loadAll();
              } else if (PropertyNamer.isSetter(methodName)) {
                // 获取属性名称
                final String property = PropertyNamer.methodToProperty(methodName);
                lazyLoader.remove(property);
              } else if (PropertyNamer.isGetter(methodName)) {
                // 获取属性名称
                final String property = PropertyNamer.methodToProperty(methodName);
                if (lazyLoader.hasLoader(property)) { // 检测是否为延迟加载的属性
                  lazyLoader.load(property);  // 触发该属性的加载操作
                }
              }
            }
          }
        }
        // 调用目标对象的方法（即enhanced父类的方法）
        return methodProxy.invokeSuper(enhanced, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodInterceptor {

    private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                             List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    /**
     * 创建代理对象，并将原始对象的属性值赋给代理对象
     *
     * @param target
     * @param unloadedProperties
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     * @return
     */
    public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                     List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      // 将原始对象的属性值赋值给代理对象
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
      final Object o = super.invoke(enhanced, method, args);
      return o instanceof AbstractSerialStateHolder ? o : methodProxy.invokeSuper(o, args);
    }

    @Override
    protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                             List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      return new CglibSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }
  }

  private static class LogHolder {
    private static final Log log = LogFactory.getLog(CglibProxyFactory.class);
  }

}
