package com.huazai.mapper;

import com.huazai.bean.Author;

import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:10:24
 */
public interface AuthorMapper {
  List<Author> select(Author author);

  int insert(Author author);
}
