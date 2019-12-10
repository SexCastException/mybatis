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
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.WeakHashMap;

/**
 * 引用强度：强引用 > 软引用 > 弱引用 > 幽灵（虚）引用
 * <p>
 * 弱引用的强度比软引用的强度还要弱。弱引用使用WeakReference 来表示，它可以引用一个对象，但并不阻止被引用的对象被GC回收。
 * 在JVM虚拟机进行垃圾回收时,如果指向一个对象的所有引用都是弱引用，那么该对象会被回收。由此可见，只被弱引用所指向的对象的
 * 生存周期是两次GC之间的这段时间，而只被软引用所指向的对象可以经历多次GC,直到出现内存紧张的情况才被回收。
 * <p>
 * 弱引用典型的应用情景是就是JDK提供的{@link WeakHashMap.Entry}。WeakHashMap.Entry实现继承了WeakReference, Entry 弱引用key,
 * <p>
 * Weak Reference cache decorator.
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class WeakCache implements Cache {
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   * 引用队列，用于记录已经被GC回收的缓存项所对应的WeakEntry对象
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

  public WeakCache(Cache delegate) {
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

  @Override
  public void putObject(Object key, Object value) {
    removeGarbageCollectedItems();
    delegate.putObject(key, new WeakEntry(key, value, queueOfGarbageCollectedEntries));
  }

  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
      // 封装缓存的弱引用对象
      WeakReference<Object> weakReference = (WeakReference<Object>) delegate.getObject(key);
    if (weakReference != null) {
      result = weakReference.get();
      if (result == null) { // 检测缓存对象是否被GC回收了
        // 如果被GC回收掉了，则通过key移除该缓存
        delegate.removeObject(key);
      } else {
        // 将没有被GC回收的缓存项的value添加到hardLinksToAvoidGarbageCollection集合中保存
        hardLinksToAvoidGarbageCollection.addFirst(result);
        // 检测 hardLinksToAvoidGarbageCollection 的大小是否超过配置的数量，如果超过则从集合中移除最早的缓存
        if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
          // 从队尾中移除最早的缓存
          hardLinksToAvoidGarbageCollection.removeLast();
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    hardLinksToAvoidGarbageCollection.clear();
    removeGarbageCollectedItems();
    delegate.clear();
  }

  private void removeGarbageCollectedItems() {
    WeakEntry sv;
    while ((sv = (WeakEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  private static class WeakEntry extends WeakReference<Object> {
    private final Object key;

    private WeakEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      this.key = key;
    }
  }

}
