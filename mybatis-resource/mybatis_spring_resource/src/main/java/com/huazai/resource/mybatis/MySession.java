package com.huazai.resource.mybatis;

import java.lang.reflect.Proxy;

/**
 * sqlSession.getMapper，通过动态代理对xxxMapper接口进行实例化
 * <p>
 * 动态代理实例化所以完成的事情：
 * 1、解析sql语句
 * 2、执行sql语句
 */
public class MySession {
    public static Object getMapper(Class clazz) {
        Object proxy = Proxy.newProxyInstance(MySession.class.getClassLoader(), new Class[]{clazz}, new UserMapperProxy());
        return proxy;
    }
}
