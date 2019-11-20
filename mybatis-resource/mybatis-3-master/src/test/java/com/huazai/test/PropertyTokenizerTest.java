package com.huazai.test;

import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
