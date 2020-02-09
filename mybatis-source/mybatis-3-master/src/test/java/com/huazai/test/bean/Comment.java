package com.huazai.test.bean;

import java.io.Serializable;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:09:06
 */
public class Comment implements Serializable {
  private Integer id;
  private Post post;
  private String content;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Post getPost() {
    return post;
  }

  public void setPost(Post post) {
    this.post = post;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
