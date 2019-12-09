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

import java.util.Deque;
import java.util.LinkedList;

/**
 * FifoCache 是先入先出版本的装饰器，当向缓存添加数据时，如果缓存项的个数已经达到上限，则会将缓存中最早进入的缓存项删除。
 * FIFO (first in, first out) cache decorator.
 *
 * @author Clinton Begin
 */
public class FifoCache implements Cache {

  /**
   * 被装饰的缓存对象
   */
  private final Cache delegate;
  /**
   * 用于记录key进入缓存的先后顺序，使用的是{@link LinkedList<Object>}类型的集合对象
   */
  private final Deque<Object> keyList;
  /**
   * 记录了缓存项的上限，超过该值，则需要清理最早的缓存项，默认值：1024
   */
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<>();
    this.size = 1024;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.size = size;
  }

  /**
   * 完成缓存项个数的检测以及缓存的清理操作
   *
   * @param key   Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  @Override
  public void putObject(Object key, Object value) {
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  private void cycleKeyList(Object key) {
    // 记录缓存
    keyList.addLast(key);
    if (keyList.size() > size) {  // 如果缓存达到上限，则清空最早的缓存
      Object oldestKey = keyList.removeFirst();
      delegate.removeObject(oldestKey);
    }
  }

}
