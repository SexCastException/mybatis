package com.example.mybatis_spring.dao;

import com.example.mybatis_spring.bean.User;

public interface UserDao {
	public User findUserById(int id);
}
