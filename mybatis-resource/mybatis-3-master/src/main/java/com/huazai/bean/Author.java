package com.huazai.bean;

import java.io.Serializable;
import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:03:07
 */
public class Author implements Serializable {
  private int authorId;
  private String username;
  private String password;
  private String email;
  private List<Blog> blogs;

  public int getAuthorId() {
    return authorId;
  }

  public void setAuthorId(int authorId) {
    this.authorId = authorId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public List<Blog> getBlogs() {
    return blogs;
  }

  public void setBlogs(List<Blog> blogs) {
    this.blogs = blogs;
  }
}
