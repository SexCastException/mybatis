package com.example.mybatis_spring.mapper;

import com.example.mybatis_spring.bean.User;

public interface UserMapper {
	public User findUserById(int id) throws Exception;
}
