package com.example.mybatis;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.example.mybatis.onetoone.bean.User;
import com.example.mybatis.onetoone.mapper.Mapper;
import com.example.mybatis.onetoone.utils.SqlSessionFactoryUtil;

/**
 * һ�������������
 * 
 * @author ��Ӣ��
 *
 */
public class CachTest {

	@org.junit.Test
	public void testFirstCache() throws IOException {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();

		User user = session.selectOne("findUserById", 2222);
		// session.commit();
		// session.clearCache();
		User user1 = session.selectOne("findUserById", 2222);
		System.out.println(user);
		System.out.println(user1);
	}

	@org.junit.Test
	public void testSecondCache() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		SqlSession session1 = sessionFactory.openSession();
		SqlSession session2 = sessionFactory.openSession();

		Mapper mapper = session.getMapper(Mapper.class);
		Mapper mapper1 = session1.getMapper(Mapper.class);
		Mapper mapper2 = session2.getMapper(Mapper.class);

		User user = mapper.findUserById(2222);
		// ����رղŻ������д������������
		session.close();

		/*User user1 = mapper1.findUserById(2222);
		User u = new User();
		u.setId(2222);
		u.setUsername("���");
		mapper1.updateUser(u);
		session1.commit();
		session1.close();*/

		User user2 = mapper2.findUserById(2222);
		session2.close();
	}
	
	/**
	 * ���Զ������治�ô�����mapper
	 * @throws IOException
	 */
	@org.junit.Test
	public void testSecondCache1() throws IOException {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		SqlSession session1 = sessionFactory.openSession();

		User user = session.selectOne("findUserById", 2222);
		session.close();
		User user1 = session1.selectOne("findUserById", 2222);
		
		System.out.println(user);
		System.out.println(user1);
	}
}
