/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.huazai.crud;

import com.huazai.bean.Author;
import com.huazai.mapper.AuthorMapper;
import com.huazai.utils.SqlSessionUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:01:18
 */
public class BootStrapAuthor {
  SqlSession sqlSession = SqlSessionUtils.getSqlSession();

  public BootStrapAuthor() throws IOException {
  }

  @Test
  public void insert() throws IOException {

    Map<String, Object> hashMap = new HashMap<>();
    hashMap.put("username", "zhangsan");
    hashMap.put("password", "654321");
    hashMap.put("email", "zhangsan@qq.com");
    Author author = new Author();
    author.setUsername("lisi");
    author.setPassword("123456");
    author.setEmail("lisi@qq.com");
    int insert = sqlSession.insert("com.huazai.mapper.AuthorMapper.insert", author);
    sqlSession.commit();
    SqlSessionUtils.close(sqlSession);
    System.out.println(insert);
  }

  @Test
  public void select() {
    AuthorMapper mapper = sqlSession.getMapper(AuthorMapper.class);
    System.out.println(mapper.select(null));
  }
}
