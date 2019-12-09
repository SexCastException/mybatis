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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BlockingCache是阻塞版本的缓存装饰器，它会保证只有-个线程到数据库中查找指定key对应的数据。
 * <p>
 * Simple blocking decorator
 * <p>
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * @author Eduardo Macarron
 */
public class BlockingCache implements Cache {

  /**
   * 阻塞超时时长
   */
  private long timeout;
  /**
   * 被装饰的Cache对象
   */
  private final Cache delegate;
  /**
   * 每 key 都有对应的 {@link ReentrantLock}  对象
   */
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
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
   * 向缓存中添加对象，并释放锁
   *
   * @param key   Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 获取key的锁
    acquireLock(key);
    Object value = delegate.getObject(key);
    // 通过key获取到对应的缓存，则释放锁，否则继续持有该锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 通过key获取 {@link ReentrantLock}对象，获取不到则创建
   *
   * @param key
   * @return
   */
  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  /**
   * acquireLock() 方法中会尝试获取指定key对应的锁。如果该key没有对应的锁对象则为其创建新的{@link ReentrantLock}对象，
   * 再加锁;如果获取锁失败，则阻塞一段时间。
   *
   * @param key
   */
  private void acquireLock(Object key) {
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock();
    }
  }

  /**
   * 释放锁
   *
   * @param key
   */
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock.isHeldByCurrentThread()) { // 锁是否被当前线程持有
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
