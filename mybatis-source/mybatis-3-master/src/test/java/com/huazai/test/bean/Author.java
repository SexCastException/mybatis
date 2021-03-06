package com.huazai.test.bean;

import java.io.Serializable;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:03:07
 */
public class Author implements Serializable {
  private Integer id;
  private String username;
  private String password;
  private String email;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
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

  public Author() {
  }

  public Author(Integer id) {
    this.id = id;
  }

  public Author(String username, String password, String email) {
    this.username = username;
    this.password = password;
    this.email = email;
  }

  public Author(Integer id, String username, String password, String email) {
    this.id = id;
    this.username = username;
    this.password = password;
    this.email = email;
  }

  @Override
  public String toString() {
    return "Author{" +
      "id=" + id +
      ", username='" + username + '\'' +
      ", password='" + password + '\'' +
      ", email='" + email + '\'' +
      '}';
  }
}
