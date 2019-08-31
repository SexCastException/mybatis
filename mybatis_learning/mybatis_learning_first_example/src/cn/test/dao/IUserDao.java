package cn.test.dao;

import java.util.List;

import cn.test.bean.User;

public interface IUserDao {

	/**
	 * ͨ���û�id�����û�
	 * 
	 * @param id
	 *            �û�id
	 * @return �û�
	 * @throws Exception
	 */
	public User findUserById(int id) throws Exception;

	/**
	 * ͨ���û���ģ����ѯ�û�
	 * 
	 * @param name
	 *            �û���
	 * @return �û���list����
	 * @throws Exception
	 */
	public List<User> findUserByName(String name) throws Exception;

	/**
	 * ���ݴ������Ĳ��������ݿ���Ӽ�¼
	 * @param user
	 * @throws Exception
	 */
	public void insertUser(User user) throws Exception;

	/**
	 * ͨ���û�idɾ���û�
	 * 
	 * @param id
	 *            �û�id
	 * @throws Exception
	 */
	public void deleteById(int id) throws Exception;

	/**
	 * ͨ���������Ĳ��������û�
	 * 
	 * @param user
	 *            �û�����
	 * @throws Exception
	 */
	public void updateUser(User user) throws Exception;
}
