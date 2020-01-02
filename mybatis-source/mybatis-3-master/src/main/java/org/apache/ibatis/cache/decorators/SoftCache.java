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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;

/**
 * 引用强度：强引用 > 软引用 > 弱引用 > 幽灵（虚）引用
 * <p>
 * <p>
 * 软引用是引用强度仅弱于强引用的一种引用，它使用类SoftReference来表示。
 * 当Java虚拟机内存不足时，GC会回收那些只被软引用指向的对象，从而避免内存溢出。
 * 在GC释放了那些只被软引用指向的对象之后，虛拟机内存依然不足，才会抛出{@link OutOfMemoryError}异常。
 * 软引用适合引用那些可以通过其他方式恢复的对象，例如，数据库缓存中的对象就可以从数据库中恢复，所以软引用可以用来实现缓存。
 * <p>
 * SoftCache就是通过软引用实现的。
 * <p>
 * 另外，由于在程序使用软引用之前的某个时刻，其所指向的对象可能已经被GC回收掉了，所以通过Reference.get()方法来获取软引用所指向的对象时，
 * 总是要通过检查该方法返回值是否为null,来判断被软引用的对象是否还存活。
 * <p>
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class SoftCache implements Cache {

  /**
   * 在SoftCache中，最近使用的一部分缓存项不会被GC回收，
   * 这就是通过将其value添加到hardLinksToAvoidGarbageCollection集合中实现的(即有强引用指向其value)
   * hardLinksToAvoidGarbageCollection集合是{@link LinkedList<Object>}类型
   */
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   * 引用队列，用于记录已经被GC回收的缓存项所对应的SoftEntry对象
   */
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  /**
   * 底层被装饰的Cache对象
   */
  private final Cache delegate;
  /**
   * 强连接的个数，默认是256
   */
  private int numberOfHardLinks;

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }


  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  /**
   * 除了向缓存中添加缓存项，还会从缓存中清除已经被GC回收的缓存项
   *
   * @param key   Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  @Override
  public void putObject(Object key, Object value) {
    // 清除已经被GC回收的缓存项
    removeGarbageCollectedItems();
    // 添加缓存项
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  /**
   * 除了从缓存中查找对应的value, 处理被GC回收的value对应的缓存项，还会更新hardLinksToAvoidGarbageCollection集合。
   *
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
      // 获取封装缓存的软引用对象
      SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    if (softReference != null) {
      result = softReference.get();
      if (result == null) { // 检测缓存是否被GC回收掉了
        // 如果被GC回收掉了，则通过key移除该缓存
        delegate.removeObject(key);
      } else {  // 否则未被GC回收
        // See #586 (and #335) modifications need more than a read lock
        synchronized (hardLinksToAvoidGarbageCollection) {
          // 将没有被GC回收的缓存项的value添加到hardLinksToAvoidGarbageCollection集合中保存
          hardLinksToAvoidGarbageCollection.addFirst(result);
          // 检测 hardLinksToAvoidGarbageCollection 的大小是否超过配置的数量，如果超过则从集合中移除最早的缓存
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            // 从队尾中移除最早的缓存
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  /**
   * 除了移除缓存项，还会从缓存中清除已经被GC回收的缓存项
   *
   * @param key The key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();  // 清空强引用集合
    }
    removeGarbageCollectedItems();  // 清空被GC回收的缓存
    delegate.clear(); // 清空被修饰缓存对象的缓存
  }

  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 遍历集合
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  /**
   * SoftEntry 继承了软引用，指向key的是强引用，指向value的是弱引用
   */
  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      // 指向value的引用是软引用，且关联了引用队列
      super(value, garbageCollectionQueue);
      // 强引用
      this.key = key;
    }
  }

}
