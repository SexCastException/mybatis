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

import java.util.concurrent.TimeUnit;

/**
 * ScheduledCache是周期性清理缓存的装饰器，它的 {@link clearInterval} 字段记录了两次缓存清理之
 * 间的时间间隔，默认是一小时，lastClear 字段记录了最近一次清理缓存的时间戳。
 * <p>
 * ScheduledCache的getObject()、 putObject()、 removeObject()等核心方法在执行时，都会根据这两个字段检测是
 * 否需要进行清理操作，清理操作会清空缓存中所有缓存项。
 *
 * @author Clinton Begin
 */
public class ScheduledCache implements Cache {

  /**
   * 底层封装的被装饰的缓存对象
   */
  private final Cache delegate;
  /**
   * 两次缓存清理之间的时间间隔，默认是一小时
   */
  protected long clearInterval;
  /**
   * 最近一次清理的时间戳
   */
  protected long lastClear;

  public ScheduledCache(Cache delegate) {
    this.delegate = delegate;
    // 清理缓存时间间隔，默认一个小时
    this.clearInterval = TimeUnit.HOURS.toMillis(1);
    this.lastClear = System.currentTimeMillis();
  }

  public void setClearInterval(long clearInterval) {
    this.clearInterval = clearInterval;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    clearWhenStale();
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object object) {
    clearWhenStale();
    delegate.putObject(key, object);
  }

  @Override
  public Object getObject(Object key) {
    return clearWhenStale() ? null : delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    clearWhenStale();
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   */
  @Override
  public void clear() {
    // 更新最后一次清空缓存的时间戳
    lastClear = System.currentTimeMillis();
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
   * 清空超过时间间隔的缓存
   *
   * @return
   */
  private boolean clearWhenStale() {
    if (System.currentTimeMillis() - lastClear > clearInterval) {
      clear();
      return true;
    }
    return false;
  }

}
