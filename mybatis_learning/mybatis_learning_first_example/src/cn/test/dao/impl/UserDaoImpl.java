package cn.test.dao.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import cn.test.bean.User;
import cn.test.dao.IUserDao;

public class UserDaoImpl implements IUserDao {

	private static SqlSessionFactory sessionFactory;

	{
		try {
			InputStream inputStream = Resources.getResourceAsStream("SqlMapConfig.xml");
			sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public User findUserById(int id) throws Exception {
		SqlSession session = sessionFactory.openSession();
		User user = session.selectOne("test.findUserById", id);
		return user;
	}

	@Override
	public List<User> findUserByName(String name) throws Exception {
		SqlSession session = sessionFactory.openSession();
		List<User> users = session.selectList("test.findUserByName", name);
		return users;
	}

	@Override
	public void insertUser(User user) throws Exception {
		SqlSession session = sessionFactory.openSession();
		session.insert("test.addUser", user);
		session.commit();
		session.close();
	}

	@Override
	public void deleteById(int id) throws Exception {
		SqlSession session = sessionFactory.openSession();
		session.delete("test.deleteById", id);
		session.commit();
		session.close();
	}

	@Override
	public void updateUser(User user) throws Exception {
		SqlSession session = sessionFactory.openSession();
		session.update("test.updateUser", user);
		session.commit();
		session.close();
	}

}
