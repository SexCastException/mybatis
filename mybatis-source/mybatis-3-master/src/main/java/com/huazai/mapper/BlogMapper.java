package com.huazai.mapper;

import com.huazai.bean.Blog;
import com.huazai.bean.Comment;

import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:10:47
 */
public interface BlogMapper {
  Blog selectBlogDetail(Blog blog);

  List<Comment> selectComment(Integer postId);
}
