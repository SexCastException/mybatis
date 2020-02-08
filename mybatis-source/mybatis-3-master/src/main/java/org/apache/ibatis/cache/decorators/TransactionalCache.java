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
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.SqlSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 继承了 {@link Cache}接口，主要用于保存在某个 {@link SqlSession}的某个事务中需要向某个二级缓存中添加的缓存数据。
 * <p>
 * The 2nd level cache transactional buffer.
 * <p>
 * 翻译：该类保存会话期间要添加到第2级缓存中的所有缓存项。如果会话回滚，则在调用commit或丢弃commit时将条目发送到缓存。<br>
 * 添加了对阻塞缓存的支持。因此，返回缓存未命中的任何get()后接put()，因此可以释放与密钥关联的任何锁。<br>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 底层封装的二级缓存所对应的 {@link Cache}对象
   */
  private final Cache delegate;
  /**
   * 当该字段为true时，则表示当前TransactionalCache不可查询，且提交事务时会将底层 {@link Cache}清空
   */
  private boolean clearOnCommit;
  /**
   * 暂时记录添加到TransactionalCache中的数据。在事务提交时，会将其中的数据添加到二级缓存中
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 记录缓存未命中的 {@link CacheKey}对象
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
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
   * 首先会查询底层的二级缓存，并将未命中的key记录到 {@link TransactionalCache#entriesMissedInCache}中，之后会根据
   * {@link TransactionalCache#clearOnCommit}字段的值决定具体的返回值。
   *
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    // 获取缓存
    Object object = delegate.getObject(key);
    if (object == null) { // 未命中缓存
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  /**
   * 该方法并没有直接将结果对象记录到其封装的二级缓存中，而是暂时保存在{@link TransactionalCache#entriesToAddOnCommit}集合中，
   * 在事务提交时才会将这些结果对象从{@link TransactionalCache#entriesToAddOnCommit}集合添加到二级缓存中。
   *
   * @param key    Can be any object but usually it is a {@link CacheKey}
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空 {@link TransactionalCache#entriesToAddOnCommit}集合，并设置 {@link TransactionalCache#clearOnCommit}为true。
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 根据 {@link TransactionalCache#clearOnCommit}字段的值决定是否清空二级缓存，然后调用 {@link TransactionalCache#flushPendingEntries}
   * 方法将 {@link TransactionalCache#entriesToAddOnCommit}集合中记录的结果对象保存到二级缓存中。
   */
  public void commit() {
    if (clearOnCommit) {
      // 提交事务前，清空二级缓存
      delegate.clear();
    }
    // 将entriesToAddOnCommit集合和entriesToAddOnCommit集合中的数据保存到二级缓存
    flushPendingEntries();
    // 重置clearOnCommit为false,并清空entriesToAddOnCommit、entriesMissedInCache集合
    reset();
  }

  public void rollback() {
    // 将entriesMissedInCache集合中记录的缓存项从二级缓存中删除
    unlockMissedEntries();
    // 重置clearOnCommit为false,并清空entriesToAddOnCommit、entriesMissedInCache集合
    reset();
  }

  /**
   * 将 {@link TransactionalCache#entriesMissedInCache}集合中记录的缓存项从二级缓存中删除，并清空
   * {@link TransactionalCache#entriesToAddOnCommit}集合和 {@link TransactionalCache#entriesMissedInCache}集合。
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    // 遍历entriesToAddOnCommit集合，将其中记录的缓存项添加到二级缓存中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 遍历entriesMissedInCache集合，将entriesToAddOnCommit集合中不包含的缓存项添加到二级缓存中
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    // 遍历entriesMissedInCache集合，将其中的缓存项从二级缓存中移除
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
          + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
