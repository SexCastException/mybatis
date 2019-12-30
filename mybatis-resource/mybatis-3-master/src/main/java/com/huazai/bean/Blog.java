package com.huazai.bean;

import java.io.Serializable;
import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:04:16
 */
public class Blog implements Serializable {
  private Integer id;
  private String title;
  private Author author;
  private List<Post> posts;

  public static String staticField;

  public Blog() {
  }

  public Blog(Integer id, String title, Author author, List<Post> posts) {
    this.id = id;
    this.title = title;
    this.author = author;
    this.posts = posts;
  }

  public Blog(Integer id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
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

  public List<Post> getPosts() {
    return posts;
  }

  public void setPosts(List<Post> posts) {
    this.posts = posts;
  }

  @Override
  public String toString() {
    return "Blog{" +
      "id=" + id +
      ", title='" + title + '\'' +
      ", posts=" + posts +
      '}';
  }
}
