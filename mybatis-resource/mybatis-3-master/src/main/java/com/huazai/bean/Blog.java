package com.huazai.bean;

import java.io.Serializable;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:04:16
 */
public class Blog implements Serializable {
  private int blogId;
  private String title;
  private Author author;

  public int getBlogId() {
    return blogId;
  }

  public void setBlogId(int blogId) {
    this.blogId = blogId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Author getAuthor() {
    return author;
  }

  public void setAuthor(Author author) {
    this.author = author;
  }
}
