package cn.test.mybatis;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import cn.test.bean.User;
import cn.test.bean.UserCustom;
import cn.test.bean.UserQueryPo;
import cn.test.mapper.UserMapper;
import cn.test.utils.SqlSessionFactoryUtil;

public class MapperTest {

	@Test
	public void findUserByIDs() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		UserQueryPo userQueryPo = new UserQueryPo();
		List<Integer> ids = new ArrayList<Integer>();
		ids.add(22);
		ids.add(25);
		ids.add(312);
		userQueryPo.setIds(ids);
		List<User> users = userMapper.findUserByIDs(userQueryPo);
		session.close();
		System.out.println(users);
	}

	@Test
	public void findUserListByDynamicSQL() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		UserQueryPo userQueryPo = new UserQueryPo();
		UserCustom userCustom = new UserCustom();
		userCustom.setUsername("花");
		userCustom.setSex("1");
		userQueryPo.setUserCustom(userCustom);
		List<UserCustom> list = userMapper.findUserListByDynamicSQL(userQueryPo);
		session.close();
		System.out.println(list);
	}

	@Test
	public void testFindUserListByResultMap() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		List<UserCustom> list = userMapper.findUserListByResultMap(316);
		session.close();
		System.out.println(list);
	}

	@Test
	public void testFindUserListByMap() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("sex", "1");
		map.put("username", "花");
		List<UserCustom> list = userMapper.findUserListByMap(map);

		session.commit();
		session.close();
		System.out.println(list);
	}

	@Test
	public void testFindUserList() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		UserQueryPo userQueryPo = new UserQueryPo();
		UserCustom userCustom = new UserCustom();
		userCustom.setSex("1");
		userCustom.setUsername("花");
		User user = new User();
		user.setSex("1");
		user.setUsername("花");
		userQueryPo.setUserCustom(userCustom);
		List<UserCustom> list = userMapper.findUserList(userQueryPo);
		session.commit();
		session.close();
		System.out.println(list);
	}

	@Test
	public void testFindUserById() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		User user = userMapper.findUserById(24);
		session.close();
		System.out.println(user);
	}

	@Test
	public void testFindUserByName() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		List<User> users = userMapper.findUserByName("二");
		session.close();
		System.out.println(users);
	}

	@Test
	public void testdeleteById() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		userMapper.deleteById(319);
		// session.commit();
		session.close();
	}

	@Test
	public void testUpdateUser() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		User user = new User();
		user.setId(310);
		user.setBirthday(new Date());
		user.setUsername("hdfhdgfh");
		userMapper.updateUser(user);
		session.commit();
		session.close();
	}

	@Test
	public void testInsert() throws Exception {
		SqlSession session = SqlSessionFactoryUtil.getSqlSessionFactory().openSession();
		UserMapper userMapper = session.getMapper(UserMapper.class);
		User user = new User();
		user.setBirthday(new Date());
		user.setUsername("花花");
		userMapper.addUser(user);
		session.commit();
		session.close();
	}

}
