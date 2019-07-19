package com.example.mybatis_spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.example.mybatis_spring.bean.User;
import com.example.mybatis_spring.dao.UserDao;
import com.example.mybatis_spring.mapper.UserMapper;

public class Test {
	private ApplicationContext applicationContext;

	@org.junit.Before
	public void before() {
		applicationContext = new ClassPathXmlApplicationContext("ApplicationContext.xml");
	}

	/**
	 * ԭʼdao��������
	 */
	@org.junit.Test
	public void testDao() {
		UserDao userDao = (UserDao) applicationContext.getBean("userDao");
		User user = userDao.findUserById(2222);
		System.out.println(user);
	}
	
	/**
	 * mapper����������
	 * @throws Exception 
	 */
	@org.junit.Test
	public void testMapper() throws Exception{
		UserMapper mapper = (UserMapper) applicationContext.getBean(UserMapper.class);
//		User user = mapper.findUserById(2222);
//		System.out.println(user);
	}

}
