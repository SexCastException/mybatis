package cn.test.utils;

import java.io.IOException;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class SqlSessionFactoryUtil {

	private static SqlSessionFactory sessionFactory;

	public static SqlSessionFactory getSqlSessionFactory() {
		try {
			if (sessionFactory == null) {
				sessionFactory = new SqlSessionFactoryBuilder()
						.build(Resources.getResourceAsStream("SqlMapConfig.xml"));
			}
			return sessionFactory;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
