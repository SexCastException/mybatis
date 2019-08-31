package com.example.mybatis.onetoone.mapper;

import java.util.List;

import com.example.mybatis.onetoone.bean.Orders;
import com.example.mybatis.onetoone.bean.OrdersAndUser;
import com.example.mybatis.onetoone.bean.User;

public interface Mapper {
	/**
	 * ͨ��resultTypeӳ��һ��һ
	 * 
	 * @return
	 */
	public List<OrdersAndUser> findOrderByUser() throws Exception;

	/**
	 * ͨ��resultMapӳ��һ��һ
	 * 
	 * @return
	 */
	public List<Orders> findOrderByUser1() throws Exception;

	/**
	 * ͨ��resultMapӳ��һ�Զ�
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrderUserAndOrderDetail() throws Exception;

	/**
	 * ͨ��resultMap����user,order,orderDetail,items��ӳ��
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<User> findUserOrdersOrderDetailItem() throws Exception;

	/**
	 * ͨ��associationʵ���ӳټ���
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrdersUserLazyLoading() throws Exception;

	/**
	 * ͨ��collectionʵ���ӳټ���
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrdersOrderDetailLazuLoading() throws Exception;

	/**
	 * ���Զ�������
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public User findUserById(int id) throws Exception;

	/**
	 * �޸��û���
	 * 
	 * @param username
	 * @throws Exception
	 */
	public void updateUser(User user) throws Exception;
}
