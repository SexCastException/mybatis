package com.huazai.test;

import org.junit.jupiter.api.Test;

import java.util.StringTokenizer;

/**
 * @author pyh
 * @date 2019/11/27 0:41
 */
public class MyTest {
  @Test
  public void testStringTokenizer() {
    String original = "  pang                    ying  hua                     ";
    StringTokenizer whitespaceStripper = new StringTokenizer(original,"g",false);
    StringBuilder builder = new StringBuilder();
    while (whitespaceStripper.hasMoreTokens()) {
      builder.append(whitespaceStripper.nextToken());
      builder.append(" ");
    }
    System.out.println(builder.toString());
  }
}
