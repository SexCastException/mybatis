package com.huazai.bean;

import java.io.Serializable;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:09:06
 */
public class Comment implements Serializable {
  private int commentId;
  private Post post;
  private String content;

  public int getCommentId() {
    return commentId;
  }

  public void setCommentId(int commentId) {
    this.commentId = commentId;
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
