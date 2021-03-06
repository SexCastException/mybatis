package com.huazai.test.mapper;

import com.huazai.test.bean.Author;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:10:24
 */
public interface AuthorMapper {
  List<Author> selectList(Author author, String testParam1, String testParam2);

  int insert(Author author);

  @Select("select * from author where id = #{arg0}")
//  @ResultMap("authorResult")
  List<Author> selectByProperty(String id, String name);

  List<String> selectUsername();

  Integer batchInsertForKeyGenerator(List<Author> authors);

  Integer insertForSelectKey(Author author);
}
