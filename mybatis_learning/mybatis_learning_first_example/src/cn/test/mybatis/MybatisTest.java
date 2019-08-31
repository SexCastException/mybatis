package cn.test.mybatis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import cn.test.bean.User;

public class MybatisTest {

	final String config = "SqlMapConfig.xml";

	@Test
	public void testSelectOne() throws IOException {
		InputStream inputStream = Resources.getResourceAsStream(config);
		SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		SqlSession session = sessionFactory.openSession();
		// 第一个参数:映射文件statement的id,等于命名空间.id
		// 第二个参数:指定匹配映射文件parameterType的参数类型
		User user = session.selectOne("test.findUserById", 16);
		System.out.println(user);
		session.close();
	}

	@Test
	public void testSelectList() throws IOException {
		SqlSessionFactory sessionFacotry = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(config));
		SqlSession session = sessionFacotry.openSession();
		List<User> users = session.selectList("test.findUserByName", "狗");
		System.out.println(users);
		session.close();
	}

	@Test
	public void testAddUser() throws IOException {
		InputStream inputStream = Resources.getResourceAsStream(config);
		SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		SqlSession session = sessionFactory.openSession();
		User user = new User();
		user.setUsername("楼上小王");
		user.setAddress("China");
		user.setSex("1");
		user.setBirthday(new Date());
		int rows = session.insert("test.addUser", user);
		session.commit();
		/*System.out.println("id:" + user.getId());*/
		System.out.println("影响的行数:" + rows);
		session.close();
	}

	@Test
	public void deleteUser() throws IOException {
		SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(config));
		SqlSession session = sessionFactory.openSession();
		session.delete("test.deleteById", 307);
		session.commit();
	}

	@Test
	public void updateUser() throws IOException {
		SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(config));
		SqlSession session = sessionFactory.openSession();
		User user = new User();
		user.setId(308);
		user.setUsername("aaa");
		session.update("test.updateUser", user);
		session.commit();
	}

}
