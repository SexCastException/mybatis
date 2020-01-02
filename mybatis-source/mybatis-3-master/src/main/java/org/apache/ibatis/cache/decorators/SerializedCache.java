/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.io.Resources;

import java.io.*;

/**
 * SerializedCache提供了将value对象序列化的功能。
 * <p>
 * SerializedCache 在添加缓存项时，会将value对应的Java 对象进行序列化，并将序列化后的byte[]数组作为value 存入缓存。
 * SerializedCache在获取缓存项时，会将缓存项中的byte[]数组反序列化成Java 对象。
 * <p>
 * 使用前面介绍的Cache装饰器实现进行装饰之后，每次从缓存中获取同一key 对应的对象时，得到的都
 * 是同一对象，任意一个线程修改该对象都会影响到其他线程以及缓存中的对象；而SerializedCache每次从缓存中获取数据时，
 * 都会通过反序列化得到一个全新的对象。
 * SerializedCache使用的序列化方式是Java原生序列化。
 *
 * @author Clinton Begin
 */
public class SerializedCache implements Cache {

  private final Cache delegate;

  public SerializedCache(Cache delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 将value对象序列化成byte[]存入缓存
   *
   * @param key    Can be any object but usually it is a {@link CacheKey}
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    if (object == null || object instanceof Serializable) {
      // 将值序列化后存入缓存中
      delegate.putObject(key, serialize((Serializable) object));
    } else {  // 对象不为null,且没有实现序列化接口，则抛出异常
      throw new CacheException("SharedCache failed to make a copy of a non-serializable object: " + object);
    }
  }

  /**
   * 通过key获取序列后的byte[]，反序列化返回
   *
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    Object object = delegate.getObject(key);
    return object == null ? null : deserialize((byte[]) object);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 序列化
   *
   * @param value
   * @return
   */
  private byte[] serialize(Serializable value) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(value);
      oos.flush();
      return bos.toByteArray();
    } catch (Exception e) {
      throw new CacheException("Error serializing object.  Cause: " + e, e);
    }
  }

  /**
   * 反序列化
   *
   * @param value
   * @return
   */
  private Serializable deserialize(byte[] value) {
    Serializable result;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
         ObjectInputStream ois = new CustomObjectInputStream(bis)) {
      result = (Serializable) ois.readObject();
    } catch (Exception e) {
      throw new CacheException("Error deserializing object.  Cause: " + e, e);
    }
    return result;
  }

  public static class CustomObjectInputStream extends ObjectInputStream {

    public CustomObjectInputStream(InputStream in) throws IOException {
      super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
      return Resources.classForName(desc.getName());
    }

  }

}
