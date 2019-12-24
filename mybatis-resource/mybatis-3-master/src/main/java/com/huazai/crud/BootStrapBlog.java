package com.huazai.crud;


import com.huazai.bean.Blog;
import com.huazai.mapper.BlogMapper;
import com.huazai.utils.SqlSessionUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:01:18
 */
public class BootStrapBlog {
  SqlSession sqlSession = SqlSessionUtils.getSqlSession();

  public BootStrapBlog() throws IOException {
  }

  @Test
  public void insert() throws IOException {
  }

  @Test
  public void selectDetail() {
    Blog blog = new Blog(1);
    BlogMapper mapper = sqlSession.getMapper(BlogMapper.class);
    Blog blog1 = mapper.selectBlogDetail(blog);
    System.out.println(blog1);
  }
}
