/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
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
