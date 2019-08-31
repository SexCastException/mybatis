package com.example.mybatis_spring.dao.impl;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.mybatis_spring.bean.User;
import com.example.mybatis_spring.dao.UserDao;

@Component
public class UserDaoImpl  extends SqlSessionDaoSupport  implements UserDao {

	/*
	 * private SqlSessionFactory sqlSessionFactory;

		public void setSqlSessionFactory(SqlSessionFactory sqlSessinFactory) {
		this.sqlSessionFactory = sqlSessinFactory;
	}*/

	@Override
	public User findUserById(int id) {
		SqlSession session = getSqlSession();
		User user = session.selectOne("findUserById", id);
		return user;
	}

}
