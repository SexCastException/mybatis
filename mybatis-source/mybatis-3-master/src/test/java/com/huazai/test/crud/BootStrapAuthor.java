package com.huazai.test.crud;

import com.huazai.test.bean.Author;
import com.huazai.test.mapper.AuthorMapper;
import com.huazai.test.utils.SqlSessionUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:01:18
 */
public class BootStrapAuthor {
  SqlSession sqlSession = SqlSessionUtils.getSqlSession();

  AuthorMapper mapper = sqlSession.getMapper(AuthorMapper.class);

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
    int insert = sqlSession.insert("com.huazai.test.mapper.AuthorMapper.insert", author);
    sqlSession.commit();
    SqlSessionUtils.close(sqlSession);
    System.out.println(insert);
  }

  @Test
  public void selectList() {
    Author author = new Author(1);
    author.setUsername("zhangsan");
    System.out.println(mapper.selectList(author, "123456", "739967221@qq.com"));
  }

  @Test
  public void selectByProperty() {
    AuthorMapper mapper = sqlSession.getMapper(AuthorMapper.class);
    List<Author> authors = mapper.selectByProperty("1", "test");
    System.out.println(authors);
  }

  @Test
  public void selectUsername() {
    List<String> usernameList = mapper.selectUsername();
    System.out.println(usernameList);
  }

  @Test
  public void testBatchInsertForKeyGenerator() {
    List<Author> authors = new ArrayList<>();
    authors.add(new Author("username", "password", "email@email.com"));
    authors.add(new Author("username1", "password1", "email1@email.com"));
    authors.add(new Author("username2", "password2", "email2@email.com"));

    Object integer = mapper.batchInsertForKeyGenerator(authors);
    sqlSession.commit();

    SqlSessionUtils.close(sqlSession);
    System.out.println(integer);
  }

  @Test
  public void testInsertForSelectKey() {
    Integer integer = mapper.insertForSelectKey(new Author("u1","p1","e1@email.com"));
    sqlSession.commit();
    System.out.println(integer);
  }
}
