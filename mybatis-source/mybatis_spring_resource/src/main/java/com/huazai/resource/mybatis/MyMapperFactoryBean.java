package com.huazai.resource.mybatis;

import org.springframework.beans.factory.FactoryBean;

/**
 * FactoryBean是一个特殊的bean
 * <p>
 * 该类实现了FactoryBean，不仅把自身加入IOC容器，而且把方法getObject加入IOC容器，通过IOC容器获取自身对象需要在名字前面加&，
 * 比如getBean("&myMapperFactoryBean");否则获取的是getObject返回对象的bean
 * <p>
 * 缺点：
 * 1、只能把单个bean加入，如果多个就要不断的创建FactoryBean的实例化
 * 2、不能传参，因为该bean在启动的时候就加入了IOC容器
 * <p>
 * 使用<bean id="" class=""></bean>配置方式可以传参，缺点，需要写太多的配置
 * <p>
 * 解决方案：通过实现ImportBeanDefintionRegistrar
 */
/*@Component*/
public class MyMapperFactoryBean implements FactoryBean {
    private Class mapperInterface;

    public MyMapperFactoryBean(Class mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public Object getObject() throws Exception {
        // 由于spring已经把userMapper加入过IOC容器，所以此操作会造成“Bean already defined with the same name!”
//        UserMapper userMapper = (UserMapper) MySession.getMapper(UserMapper.class);
//        return userMapper;

        Object object = MySession.getMapper(mapperInterface);
        return object;
    }

    public Class<?> getObjectType() {
        return mapperInterface;
    }
}
