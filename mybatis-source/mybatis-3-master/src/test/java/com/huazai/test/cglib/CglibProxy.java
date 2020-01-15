package com.huazai.test.cglib;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author pyh
 * @date 2020/1/15 23:48
 */
public class CglibProxy implements MethodInterceptor {
  /**
   * cglib的 Enhancer对象
   */
  Enhancer enhancer = new Enhancer();

  public Object getProxy(Class clazz) {
    // 指定生成代理类的父类
    enhancer.setSuperclass(clazz);
    // 设置 Callback对象
    enhancer.setCallback(this);
    // 通过字节码技术动态创建子类实例
    return enhancer.create();
  }

  @Override
  public Object intercept(Object obj, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
    System.out.println("前置处理");
    // 调用父类中的方法
    Object result = methodProxy.invokeSuper(obj, objects);
    System.out.println("后置处理");
    return result;
  }
}
