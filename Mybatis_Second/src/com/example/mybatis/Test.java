package com.example.mybatis;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.example.mybatis.onetoone.bean.OrderDetail;
import com.example.mybatis.onetoone.bean.Orders;
import com.example.mybatis.onetoone.bean.OrdersAndUser;
import com.example.mybatis.onetoone.bean.User;
import com.example.mybatis.onetoone.mapper.Mapper;
import com.example.mybatis.onetoone.utils.SqlSessionFactoryUtil;

/**
 * 关系映射测试类
 * @author 庞英华
 *
 */
public class Test {

	@org.junit.Test
	public void findOrderByUser() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<OrdersAndUser> list = mapper.findOrderByUser();
		System.out.println(list);
		session.close();
	}

	@org.junit.Test
	public void findOrderByUser1() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<Orders> list = mapper.findOrderByUser1();
		System.out.println(list);
		session.close();
	}

	@org.junit.Test
	public void testFindOrderUserAndOrderDetail() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<Orders> list = mapper.findOrderUserAndOrderDetail();
		System.out.println(list);
		System.out.println("size:" + list.get(0).getOrderDetails().size());
		session.close();
	}

	@org.junit.Test
	public void testFindUserOrdersOrderDetailItem() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<User> list = mapper.findUserOrdersOrderDetailItem();
		System.out.println(list);
		session.close();
	}

	@org.junit.Test
	public void testFindOrdersUserLazyLoading() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<Orders> orders = mapper.findOrdersUserLazyLoading();
//		session.close();
		for (Orders order : orders) {
			System.out.println(order.getUser());
		}
	}

	@org.junit.Test
	public void testFindOrdersOrderDetailLazuLoading() throws Exception {
		SqlSessionFactory sessionFactory = SqlSessionFactoryUtil.getSessionFactory();
		SqlSession session = sessionFactory.openSession();
		Mapper mapper = session.getMapper(Mapper.class);
		List<Orders> orders = mapper.findOrdersOrderDetailLazuLoading();
		session.close();
		for (Orders order : orders) {
			List<OrderDetail> orderDetails = order.getOrderDetails();
			for (OrderDetail orderDetail : orderDetails) {
				System.out.println(orderDetail+"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"+order.getId());
			}
		}
	}

}
