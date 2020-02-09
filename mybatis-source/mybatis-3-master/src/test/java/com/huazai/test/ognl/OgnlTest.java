package com.huazai.test.ognl;

import com.huazai.test.bean.Author;
import com.huazai.test.bean.Blog;
import com.huazai.test.bean.Post;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/30 14:19:28
 */
public class OgnlTest {
  private static Blog blog;

  private static Author author;

  private static List<Post> posts;

  private static OgnlContext context;

  @Before
  public void start() {
    Blog.staticField = "staticField";
    author = new Author(1, "username1", "password1", "email1");

    Post post = new Post();
    post.setContent("postContent");
    post.setAuthor(author);

    posts = new ArrayList<>();
    posts.add(post);

    blog = new Blog(1, "blogTitle", author, posts);
    context = new OgnlContext(null);
    context.put("blog", blog);
    context.setRoot(blog);
  }


  @Test
  public void test1() throws OgnlException {

    Author author = new Author(2, "username2", "password2", "email2");
    context.put("author", author);

    Object obj = Ognl.getValue(Ognl.parseExpression("author"), context, context.getRoot());
    System.out.println(obj);

    obj = Ognl.getValue(Ognl.parseExpression("author.username"), context, context.getRoot());
    System.out.println(obj);

    obj = Ognl.getValue(Ognl.parseExpression("#author.username"), context, context.getRoot());
    System.out.println(obj);
  }
}
