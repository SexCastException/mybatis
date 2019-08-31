package cn.test.mybatis;

import java.util.Date;
import java.util.List;

import org.junit.Test;

import cn.test.bean.User;
import cn.test.dao.IUserDao;
import cn.test.dao.impl.UserDaoImpl;

public class DaoTest {

	private IUserDao dao = new UserDaoImpl();

	@Test
	public void testInsert() throws Exception {

		User user = new User();
		user.setUsername("Ð¡ºì");
		user.setAddress("China");
		user.setBirthday(new Date());
		dao.insertUser(user);
		System.out.println(user);
	}

	@Test
	public void testDelete() throws Exception {
		dao.deleteById(313);
	}

	@Test
	public void testInsertByName() throws Exception {
		List<User> users = dao.findUserByName("Ð¡");
		System.out.println(users);
	}
	
	@Test
	public void testUpdate() throws Exception{
		User user = new User();
		user.setId(312);
		user.setUsername("¹·Ê£");
		user.setAddress("China");
		user.setBirthday(new Date());
		dao.updateUser(user);
	}

}
