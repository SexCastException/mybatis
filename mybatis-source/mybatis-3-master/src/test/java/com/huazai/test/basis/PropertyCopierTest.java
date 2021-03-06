package com.huazai.test.basis;

import org.apache.ibatis.reflection.property.PropertyCopier;
import org.junit.jupiter.api.Test;

/**
 * @author pyh
 * @date 2019/11/16 16:36
 */
public class PropertyCopierTest {
  @Test
  public void testCopyBeanProperties() {
    TestA test1 = new TestA("testA");
    TestA test2 = new TestA("testB");
    PropertyCopier.copyBeanProperties(TestA.class, test1, test2);
    System.out.println(test1);
  }


}

class TestA {
  private String a;

  public TestA(String a) {
    this.a = a;
  }

  @Override
  public String toString() {
    return a;
  }
}
