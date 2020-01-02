package com.huazai.test;

import org.apache.ibatis.reflection.TypeParameterResolver;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

public class TypeParameterResolverTest {

  private ClassA<String, Integer> ca = new ClassA();

  @org.junit.Test
  public void testParameterizedType() throws Exception {
    Field map = ClassA.class.getDeclaredField("map");
    Field name = ClassA.class.getDeclaredField("name");
    System.out.println(map.getGenericType());
    System.out.println(name.getGenericType());
    System.out.println(map.getGenericType() instanceof ParameterizedType);
    System.out.println(name.getGenericType() instanceof ParameterizedType);

    // 第一种方式
    Type type = TypeParameterResolver.resolveFieldType(map, ParameterizedTypeImpl.make(ClassA.class, new Type[]{Long.class, String.class}, TypeParameterResolverTest.class));
    // 第二种方式
//    Type type = TypeParameterResolver.resolveFieldType(map, TypeParameterResolverTest.class.getDeclaredField("ca").getGenericType());

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
