package com.huazai.test.utils;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author pyh
 * @date 2019/12/23 21:48
 */
public class SqlSessionUtils {
  private static String resource = "resources/huazai/mybatis-config.xml";

  public static SqlSession getSqlSession() throws IOException {
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    return sqlSessionFactory.openSession();
  }

  public static void close(SqlSession sqlSession) {
    sqlSession.close();
  }
}
