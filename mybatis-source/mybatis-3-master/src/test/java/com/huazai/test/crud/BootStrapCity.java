package com.huazai.test.crud;

import com.huazai.test.utils.SqlSessionUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author pyh
 * @date 2020/4/4 10:43
 */
public class BootStrapCity {
  SqlSession sqlSession = SqlSessionUtils.getSqlSession();


  public BootStrapCity() throws IOException {
  }

  @Test
  public void testQuery() {
    sqlSession.getConfiguration().addMapper(CityMapper.class);
    CityMapper mapper = sqlSession.getMapper(CityMapper.class);
    List<Map> list = mapper.list();
    System.out.println(list);
  }
}
