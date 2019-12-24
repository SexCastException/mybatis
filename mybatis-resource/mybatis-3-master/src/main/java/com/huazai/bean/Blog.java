/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
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

  public Blog() {
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
