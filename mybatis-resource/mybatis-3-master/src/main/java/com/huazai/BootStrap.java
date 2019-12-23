package com.huazai;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.InputStream;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:01:18
 */
public class BootStrap {
  private static String resource = "mybatis-config.xml";

  public static void main(String[] args) throws Exception {
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = sqlSessionFactory.openSession();
  }
}
