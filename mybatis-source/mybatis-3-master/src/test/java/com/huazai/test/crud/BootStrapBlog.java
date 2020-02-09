package com.huazai.test.crud;


import com.huazai.test.bean.Blog;
import com.huazai.test.mapper.BlogMapper;
import com.huazai.test.utils.SqlSessionUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:01:18
 */
public class BootStrapBlog {
  SqlSession sqlSession = SqlSessionUtils.getSqlSession();

  BlogMapper mapper = sqlSession.getMapper(BlogMapper.class);

  public BootStrapBlog() throws IOException {
  }

  @Test
  public void insert() throws IOException {
  }

  @Test
  public void selectDetail() {
    Blog blog = new Blog(1);
    Blog blog1 = mapper.selectBlogDetail(blog);
    System.out.println(blog1);
  }

  @Test
  public void selectBlog() {
    Blog blog = new Blog(1);
    List<Blog> blogs = mapper.selectBlog(1);
    System.out.println(blogs);
  }
}
