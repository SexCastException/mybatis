package com.huazai.resource.config;

import com.huazai.resource.dao.UserMapper;
import com.huazai.resource.mybatis.MyMapperScan;
import com.huazai.resource.mybatis.MySession;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * 使用MapperScan注解使用的是mybatis的mapper代理类，使用MyMapperScan使用的是自定义动态代理类
 */
@ComponentScan("com.huazai.resource")
@Configuration
/*扫描mapper的包*/
//@MapperScan("com.huazai.resource.dao")

// @MyMapperScan代替@Import(MyImportBeanDefinitionRegistrar.class)
@MyMapperScan
//@Import(MyImportBeanDefinitionRegistrar.class)
public class AppConfig {

    @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);
        return sqlSessionFactoryBean;
    }

    @Bean
    public DataSource dataSource() {
        // spring-jdbc的连接池
        DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
        driverManagerDataSource.setDriverClassName("com.mysql.jdbc.Driver");
        driverManagerDataSource.setUsername("root");
        driverManagerDataSource.setPassword("123456");
        driverManagerDataSource.setUrl("jdbc:mysql://localhost:3306/beens?characterEncoding=utf-8");
        return driverManagerDataSource;
    }

    /**
     * 将自定义的xxxMapper加入IOC容器，如果不需要则注释掉该代码，则使用mybatis动态代理的xxxMapper
     *
     * @return
     */
//    @Bean
    public UserMapper userMapper() {
        UserMapper userMapper = (UserMapper) MySession.getMapper(UserMapper.class);
        return userMapper;
    }
}
