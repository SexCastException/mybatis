package com.huazai.test.basis;

import org.junit.jupiter.api.Test;

import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * @author pyh
 * @date 2019/11/27 0:41
 */
public class MyTest {
  @Test
  public void testStringTokenizer() {
    String columnName = "{prop1=col1, prop2=co12}";
    StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
    while (parser.hasMoreTokens()) {
      String token = parser.nextToken();
      System.out.println(token);
    }
  }

  @Test
  public void testStringJoiner(){
    StringJoiner sqlBuilder = new StringJoiner(",");
    sqlBuilder.add("aa").add("bb").add(",").add("dd");
    System.out.println(sqlBuilder.toString());
  }
}
