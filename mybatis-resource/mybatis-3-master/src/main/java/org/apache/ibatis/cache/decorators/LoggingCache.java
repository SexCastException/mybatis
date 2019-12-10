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
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * LoggingCache在Cache的基础上提供了日志功能，它通过 {@link hits} 字段和 {@link requests} 字段记录了Cache的命中次数和访问次数。
 * 在LoggingCache.getObject()方法中会统计命中次数和访问次数这两个指标，并按照指定的日志输出方式输出命中率。
 *
 * @author Clinton Begin
 */
public class LoggingCache implements Cache {

  /**
   * 为缓存提供日志功能的对象
   */
  private final Log log;
  /**
   * 底层被修饰的缓存对象
   */
  private final Cache delegate;
  /**
   * Cache的访问次数
   */
  protected int requests = 0;
  /**
   * Cache命中次数
   */
  protected int hits = 0;

  public LoggingCache(Cache delegate) {
    this.delegate = delegate;
    this.log = LogFactory.getLog(getId());
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  @Override
  public Object getObject(Object key) {
    // Cache访问次数
    requests++;
    final Object value = delegate.getObject(key);
    if (value != null) {  // 通过key能获取到缓存对象，则表示Cache命中
      // 累计Cache命中次数
      hits++;
    }
    if (log.isDebugEnabled()) {
      log.debug("Cache Hit Ratio [" + getId() + "]: " + getHitRatio());
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

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
   * 命中率 = Cache命中次数 / Cache访问次数
   *
   * @return
   */
  private double getHitRatio() {
    return (double) hits / (double) requests;
  }

}
