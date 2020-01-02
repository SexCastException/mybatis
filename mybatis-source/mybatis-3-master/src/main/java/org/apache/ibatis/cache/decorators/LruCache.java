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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LruCache是按照近期最少使用算法(Least Recently Used, LRU)进行缓存清理的装饰器，
 * 在需要清理缓存时，它会清除最近最少使用的缓存项。
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  /**
   * 被装饰的Cache底层对象
   */
  private final Cache delegate;
  /**
   * {@link LinkedHashMap<Object, Object>}类型对象，它是一个有序的{@link HashMap}，用于记录key最近的使用情况
   * key和value都是保存缓存的key
   */
  private Map<Object, Object> keyMap;
  /**
   * 记录最少被使用的缓存项的key
   */
  private Object eldestKey;

  /**
   * LruCache默认缓存大小是1024
   *
   * @param delegate
   */
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 设置缓存大小，并同时初始化keyMap
    setSize(1024);
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
   * 设置缓存大小，并同时初始化 keyMap
   *
   * @param size
   */
  public void setSize(final int size) {
    // 注意LinkedHashMap构造函数的第三个参数，true表示该LinkedHashMap记录的顺序是access-order,
    // 也就是说LinkedHashMap.get()方法会改变其记录的顺序
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      /**
       * 重写了 removeEldestEntry() 方法， 并不是真正执行了移除操作，而是记录了达到缓存上限的时候指定要移除的key
       * @param eldest
       * @return 是否达到了缓存的大小的上限
       */
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) { // 如果已到达缓存上限，则更新eldestKey字段，后面会删除该项
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  /**
   * 除了添加缓存项，还会将eldestKey字段指定的缓存项清除掉。
   *
   * @param key   Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  /**
   * 除了获取缓存项，还会调用keyMap.get()方法修改key的顺序，最近使用放在队列尾部，表示指定的key最近被使用。
   *
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // 将最近被使用的key放入队列底部，队列底部以上的元素往上移动
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }

  /**
   * @param key The key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
