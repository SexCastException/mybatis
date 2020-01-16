package com.huazai.test.javassist;

import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;

/**
 * @author pyh
 * @date 2020/1/17 0:05
 */
public class JavassistMain2 {
  public static void main(String[] args) throws Throwable {
    ProxyFactory proxyFactory = new ProxyFactory();
    // 指定父类，ProxyFactory会动态生成继承该父类的子类
    proxyFactory.setSuperclass(JavassistMain.generateClass());

    // 设置过滤器，判断哪些方法需要拦截
    proxyFactory.setFilter(m -> m.getName().equals("execute"));

    // 设置拦截器
    proxyFactory.setHandler((self, thisMethod, proceed, arguments) -> {
      System.out.println("前置处理");
      Object result = proceed.invoke(self, arguments);
      System.out.println("执行结果：" + result);
      System.out.println("后置处理");
      return result;
    });

    // 创建JavassistClass的代理类，并创建代理对象
    Class<?> c = proxyFactory.createClass();
    Object o = c.newInstance();
    Object test = ((ProxyObject) o).getHandler().invoke(o, null, o.getClass().getMethod("test",
      new Class[]{String.class, int.class}),
      new Object[]{"11", 2});
    System.out.println("结果："+test);
  }
}
