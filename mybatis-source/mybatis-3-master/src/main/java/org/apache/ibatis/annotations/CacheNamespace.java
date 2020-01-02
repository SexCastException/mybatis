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
package org.apache.ibatis.annotations;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;

import java.lang.annotation.*;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheNamespace {
  /**
   * 默认被装饰底层缓存对象
   *
   * @return
   */
  Class<? extends Cache> implementation() default PerpetualCache.class;

  /**
   * 默认缓存装饰器
   *
   * @return
   */
  Class<? extends Cache> eviction() default LruCache.class;

  /**
   * 默认刷新缓存周期，不为0时，将被{@link ScheduledCache}装饰器装饰
   *
   * @return
   */
  long flushInterval() default 0;

  /**
   * 默认缓存的个数
   *
   * @return
   */
  int size() default 1024;

  /**
   * 默认缓存可读写性，为true时，将被{@link SerializedCache}装饰器装饰
   *
   * @return
   */
  boolean readWrite() default true;

  /**
   * 默认缓存不阻塞，为true时，将被{@link BlockingCache}装饰器装饰
   *
   * @return
   */
  boolean blocking() default false;

  /**
   * Property values for a implementation object.
   * 缓存对象和缓存装饰器的配置对象
   *
   * @since 3.4.2
   */
  Property[] properties() default {};

}
