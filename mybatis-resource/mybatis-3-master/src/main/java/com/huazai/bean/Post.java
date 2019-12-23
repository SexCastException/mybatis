package com.huazai.bean;

import java.io.Serializable;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:06:58
 */
public class Post implements Serializable {
  private int postId;
  private Blog blog;
  private String content;
  private int draft;

  public int getPostId() {
    return postId;
  }

  public void setPostId(int postId) {
    this.postId = postId;
  }

  public Blog getBlog() {
    return blog;
  }

  public void setBlog(Blog blog) {
    this.blog = blog;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public int getDraft() {
    return draft;
  }

  public void setDraft(int draft) {
    this.draft = draft;
  }
}
