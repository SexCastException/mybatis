package com.huazai.test;

import org.apache.ibatis.reflection.TypeParameterResolver;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

public class TypeParameterResolverTest {
  @org.junit.Test
  public void testParameterizedType() throws Exception {
    Field map = ClassA.class.getDeclaredField("map");
    System.out.println(map.getGenericType());
    System.out.println(map.getGenericType() instanceof ParameterizedType);

    // 第一个种方式
    Type type = TypeParameterResolver.resolveFieldType(map, ParameterizedTypeImpl.make(SubClassA.class, new Type[]{Long.class, String.class}, TypeParameterResolverTest.class));
    // 第二种方式
//    Type type = TypeParameterResolver.resolveFieldType(map, TypeParameterResolverTest.class.getDeclaredField("map").getGenericType());

    System.out.println(type);

    ParameterizedType p = (ParameterizedType) type;
    System.out.println(p.getRawType()); // interface java.util.Map
    System.out.println(p.getOwnerType()); // null
    // class java.lang.Long
    // class java.lang.String
    for (Type t : p.getActualTypeArguments()) {
      System.out.println(t);
    }


  }
}

class ClassA<K, V> {
  // ParameterizedType 类型
  protected Map<K, V> map;

  // Class
  private String name;

  public Map<K, V> getMap() {
    return map;
  }

  public void setMap(Map<K, V> map) {
    this.map = map;
  }
}

class SubClassA<K, V> extends ClassA<K, V> {

}
