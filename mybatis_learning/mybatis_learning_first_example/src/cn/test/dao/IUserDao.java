package cn.test.dao;

import java.util.List;

import cn.test.bean.User;

public interface IUserDao {

	/**
	 * 通过用户id查找用户
	 * 
	 * @param id
	 *            用户id
	 * @return 用户
	 * @throws Exception
	 */
	public User findUserById(int id) throws Exception;

	/**
	 * 通过用户名模糊查询用户
	 * 
	 * @param name
	 *            用户名
	 * @return 用户的list集合
	 * @throws Exception
	 */
	public List<User> findUserByName(String name) throws Exception;

	/**
	 * 根据传进来的参数向数据库添加记录
	 * @param user
	 * @throws Exception
	 */
	public void insertUser(User user) throws Exception;

	/**
	 * 通过用户id删除用户
	 * 
	 * @param id
	 *            用户id
	 * @throws Exception
	 */
	public void deleteById(int id) throws Exception;

	/**
	 * 通过传进来的参数更新用户
	 * 
	 * @param user
	 *            用户参数
	 * @throws Exception
	 */
	public void updateUser(User user) throws Exception;
}
