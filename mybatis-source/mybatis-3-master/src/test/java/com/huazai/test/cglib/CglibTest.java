package com.huazai.test.cglib;

/**
 * @author pyh
 * @date 2020/1/15 23:56
 */
public class CglibTest {
  public String method(String str) {
    System.out.println(str);
    return "CglibTest.method():" + str;
  }

  public static void main(String[] args) {
    CglibProxy cglibProxy = new CglibProxy();
    // 生成CglibTest代理对象
    CglibTest proxy = (CglibTest) cglibProxy.getProxy(CglibTest.class);
    // 调用代理对象的method方法
    String result = proxy.method("test");
    System.out.println(result);
  }
}
