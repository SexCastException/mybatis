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
		// ��һ������:ӳ���ļ�statement��id,���������ռ�.id
		// �ڶ�������:ָ��ƥ��ӳ���ļ�parameterType�Ĳ�������
		User user = session.selectOne("test.findUserById", 16);
		System.out.println(user);
		session.close();
	}

	@Test
	public void testSelectList() throws IOException {
		SqlSessionFactory sessionFacotry = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(config));
		SqlSession session = sessionFacotry.openSession();
		List<User> users = session.selectList("test.findUserByName", "��");
		System.out.println(users);
		session.close();
	}

	@Test
	public void testAddUser() throws IOException {
		InputStream inputStream = Resources.getResourceAsStream(config);
		SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		SqlSession session = sessionFactory.openSession();
		User user = new User();
		user.setUsername("¥��С��");
		user.setAddress("China");
		user.setSex("1");
		user.setBirthday(new Date());
		int rows = session.insert("test.addUser", user);
		session.commit();
		/*System.out.println("id:" + user.getId());*/
		System.out.println("Ӱ�������:" + rows);
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
