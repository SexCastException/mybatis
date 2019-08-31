package com.huazai.resource.mybatis;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MyMapperFactoryBean.class);
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        // 此处写死了，需要通过定位某个包循环获取mapper的全限定名
        try {
            beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(Class.forName("com.huazai.resource.dao.UserMapper"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        beanDefinitionRegistry.registerBeanDefinition("myMapperFactoryBean", beanDefinition);
    }
}
