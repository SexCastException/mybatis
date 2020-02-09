package com.huazai.test.basis;

import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.junit.jupiter.api.Test;

/**
 * @author pyh
 * @date 2019/11/10 18:45
 */
public class PropertyTokenizerTest {
  @Test
  public void test() {
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer("orders[O].items[O].name");
    System.out.println(propertyTokenizer);
  }
}
