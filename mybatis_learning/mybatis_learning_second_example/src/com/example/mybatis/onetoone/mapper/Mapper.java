package com.example.mybatis.onetoone.mapper;

import java.util.List;

import com.example.mybatis.onetoone.bean.Orders;
import com.example.mybatis.onetoone.bean.OrdersAndUser;
import com.example.mybatis.onetoone.bean.User;

public interface Mapper {
	/**
	 * 通过resultType映射一对一
	 * 
	 * @return
	 */
	public List<OrdersAndUser> findOrderByUser() throws Exception;

	/**
	 * 通过resultMap映射一对一
	 * 
	 * @return
	 */
	public List<Orders> findOrderByUser1() throws Exception;

	/**
	 * 通过resultMap映射一对多
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrderUserAndOrderDetail() throws Exception;

	/**
	 * 通过resultMap关联user,order,orderDetail,items表映射
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<User> findUserOrdersOrderDetailItem() throws Exception;

	/**
	 * 通过association实现延迟加载
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrdersUserLazyLoading() throws Exception;

	/**
	 * 通过collection实现延迟加载
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Orders> findOrdersOrderDetailLazuLoading() throws Exception;

	/**
	 * 测试二级缓存
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */
	public User findUserById(int id) throws Exception;

	/**
	 * 修改用户名
	 * 
	 * @param username
	 * @throws Exception
	 */
	public void updateUser(User user) throws Exception;
}
