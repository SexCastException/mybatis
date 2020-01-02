package com.huazai.resource.test;

import com.huazai.resource.config.AppConfig;
import com.huazai.resource.dao.UserMapper;
import com.huazai.resource.mybatis.MySession;
import com.huazai.resource.service.UserService;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MyTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
        UserService userService = applicationContext.getBean(UserService.class);
        userService.queryAll();
    }

    /**
     * 模拟通过动态代理获取mapper对象
     * 疑问：自己创建的对象如何加入spring IOC容器?
     */
    @Test
    public void test() {
        UserMapper userMapper = (UserMapper) MySession.getMapper(UserMapper.class);
        userMapper.query();
    }

    @Test
    public void testFactoryBean() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
        Object bean = applicationContext.getBean("userMapper");
        Object bean1 = applicationContext.getBean("myMapperFactoryBean");
        Object bean2 = applicationContext.getBean("&myMapperFactoryBean");
        System.out.println(bean);
        System.out.println(bean1);
        System.out.println(bean2);
        System.out.println(bean == bean1);
    }
}
