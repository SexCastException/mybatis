package com.huazai.resource.mybatis;

import org.apache.ibatis.annotations.Select;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class UserMapperProxy implements InvocationHandler {
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1、解析sql语句
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            // 拿到执行mapper方法注解的sql语句
            String sql = select.value()[0];
            // 2、执行sql
            System.out.println("模拟执行sql语句：" + sql);
        }

        if (method.getName().equals("toString")) {
            return proxy.getClass().getSimpleName();
        }
        return null;
    }
}
