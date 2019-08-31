package com.example.mybatis.onetoone.utils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class SqlSessionFactoryUtil {

	private final static String config = "SqlMapConfig.xml";

	private static SqlSessionFactory sessionFactory;

	public static SqlSessionFactory getSessionFactory() throws IOException {
		if (sessionFactory == null) {
			InputStream inputStream = Resources.getResourceAsStream(config);
			sessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		}
		return sessionFactory;
	}
}
